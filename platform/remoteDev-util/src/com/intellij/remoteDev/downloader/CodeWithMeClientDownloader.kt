package com.intellij.remoteDev.downloader

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileSystemUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.util.progress.withProgressText
import com.intellij.remoteDev.RemoteDevSystemSettings
import com.intellij.remoteDev.RemoteDevUtilBundle
import com.intellij.remoteDev.connection.JetBrainsClientDownloadInfo
import com.intellij.remoteDev.util.*
import com.intellij.util.PlatformUtils
import com.intellij.util.application
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtScheduler
import com.intellij.util.io.BaseOutputReader
import com.intellij.util.io.DigestUtil
import com.intellij.util.io.HttpRequests
import com.intellij.util.io.HttpRequests.HttpStatusException
import com.intellij.util.system.CpuArch
import com.intellij.util.text.VersionComparatorUtil
import com.intellij.util.withFragment
import com.intellij.util.withQuery
import com.jetbrains.infra.pgpVerifier.JetBrainsPgpConstants
import com.jetbrains.infra.pgpVerifier.JetBrainsPgpConstants.JETBRAINS_DOWNLOADS_PGP_MASTER_PUBLIC_KEY
import com.jetbrains.infra.pgpVerifier.PgpSignaturesVerifier
import com.jetbrains.infra.pgpVerifier.PgpSignaturesVerifierLogger
import com.jetbrains.infra.pgpVerifier.Sha256ChecksumSignatureVerifier
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.isAlive
import com.jetbrains.rd.util.reactive.fire
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.*
import java.nio.file.attribute.FileTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.*
import kotlin.math.min
import kotlin.time.Duration.Companion.seconds

@ApiStatus.Experimental
object CodeWithMeClientDownloader {

  private val LOG = logger<CodeWithMeClientDownloader>()

  private const val extractDirSuffix = ".ide.d"

  private val config get () = service<JetBrainsClientDownloaderConfigurationProvider>()

  private fun isJbrSymlink(file: Path): Boolean = file.name == "jbr" && isSymlink(file)
  private fun isSymlink(file: Path): Boolean = FileSystemUtil.getAttributes(file.toFile())?.isSymLink == true

  val cwmJbrManifestFilter: (Path) -> Boolean = { !it.isDirectory() || isSymlink(it) }

  fun getJetBrainsClientManifestFilter(clientBuildNumber: String): (Path) -> Boolean {
    val universalFilter: (Path) -> Boolean = if (isClientWithBundledJre(clientBuildNumber)) {
      { !it.isDirectory() || isSymlink(it) }
    } else {
      { !isJbrSymlink(it) && (!it.isDirectory() || isSymlink(it)) }
    }

    when {
      SystemInfoRt.isMac -> return { it.name != ".DS_Store" && universalFilter.invoke(it) }
      SystemInfoRt.isWindows -> return { !it.name.equals("Thumbs.db", ignoreCase = true) && universalFilter.invoke(it) }
      else -> return universalFilter
    }
  }

  private const val minimumClientBuildWithBundledJre = "223.4374"
  fun isClientWithBundledJre(clientBuildNumber: String) = clientBuildNumber.contains("SNAPSHOT") || VersionComparatorUtil.compare(clientBuildNumber, minimumClientBuildWithBundledJre) >= 0

  @ApiStatus.Internal
  class DownloadableFileData(
    val fileCaption: String,
    val url: URI,
    val archivePath: Path,
    val targetPath: Path,
    val includeInManifest: (Path) -> Boolean,
    val downloadFuture: CompletableFuture<Boolean> = CompletableFuture(),
    val status: AtomicReference<DownloadableFileState> = AtomicReference(DownloadableFileState.Downloading),
  ) {
    companion object {
      private val prohibitedFileNameChars = Regex("[^._\\-a-zA-Z0-9]")

      private fun sanitizeFileName(fileName: String) = prohibitedFileNameChars.replace(fileName, "_")

      fun build(url: URI, tempDir: Path, cachesDir: Path, includeInManifest: (Path) -> Boolean): DownloadableFileData {
        val urlWithoutFragment = url.withFragment(null)
        val bareUrl = urlWithoutFragment.withQuery(null)

        val fileNameFromUrl = sanitizeFileName(bareUrl.path.toString().substringAfterLast('/'))
        val fileName = fileNameFromUrl.take(100) +
                       "-" +
                       DigestUtil.sha256Hex(urlWithoutFragment.toString().toByteArray()).substring(0, 10)
        return DownloadableFileData(
          fileCaption = fileNameFromUrl,
          url = url,
          archivePath = tempDir.resolve(fileName),
          targetPath = cachesDir / (fileName + extractDirSuffix),
          includeInManifest = includeInManifest,
        )
      }
    }

    override fun toString(): String {
      return "DownloadableFileData(fileCaption='$fileCaption', url=$url, archivePath=$archivePath, targetPath=$targetPath)"
    }

    enum class DownloadableFileState {
      Downloading,
      Extracting,
      Done,
    }
  }

  private fun getClientDistributionName(clientBuildVersion: String) = when {
    clientBuildVersion.contains("SNAPSHOT") -> "JetBrainsClient"
    VersionComparatorUtil.compare(clientBuildVersion, "211.6167") < 0 -> "IntelliJClient"
    VersionComparatorUtil.compare(clientBuildVersion, "213.5318") < 0 -> "CodeWithMeGuest"
    else -> "JetBrainsClient"
  }

  fun createSessionInfo(clientBuildVersion: String, jreBuild: String?, unattendedMode: Boolean): JetBrainsClientDownloadInfo {
    val buildNumber = requireNotNull(BuildNumber.fromStringOrNull(clientBuildVersion)) { "Invalid build version: $clientBuildVersion" }

    val isSnapshot = buildNumber.isSnapshot
    if (isSnapshot) {
      LOG.warn("Thin client download from sources may result in failure due to different sources on host and client, " +
               "don't forget to update your locally built archive")
    }

    val bundledJre = isClientWithBundledJre(clientBuildVersion)
    val jreBuildToDownload = if (bundledJre) {
      null
    }
    else {
      jreBuild ?: error("JRE build number must be passed for client build number < $clientBuildVersion")
    }

    val hostBuildNumber = buildNumber.asStringWithoutProductCode()

    val platformSuffix = if (jreBuildToDownload != null) when {
      SystemInfo.isLinux && CpuArch.isIntel64() -> "-no-jbr.tar.gz"
      SystemInfo.isLinux && CpuArch.isArm64() -> "-no-jbr-aarch64.tar.gz"
      SystemInfo.isWindows && CpuArch.isIntel64() -> ".win.zip"
      SystemInfo.isWindows && CpuArch.isArm64() -> "-aarch64.win.zip"
      SystemInfo.isMac && CpuArch.isIntel64() -> "-no-jdk.sit"
      SystemInfo.isMac && CpuArch.isArm64() -> "-no-jdk-aarch64.sit"
      else -> null
    } else when {
      SystemInfo.isLinux && CpuArch.isIntel64() -> ".tar.gz"
      SystemInfo.isLinux && CpuArch.isArm64() -> "-aarch64.tar.gz"
      SystemInfo.isWindows && CpuArch.isIntel64() -> ".jbr.win.zip"
      SystemInfo.isWindows && CpuArch.isArm64() -> "-aarch64.jbr.win.zip"
      SystemInfo.isMac && CpuArch.isIntel64() -> ".sit"
      SystemInfo.isMac && CpuArch.isArm64() -> "-aarch64.sit"
      else -> null
    } ?: error("Current platform is not supported: OS ${SystemInfo.OS_NAME} ARCH ${SystemInfo.OS_ARCH}")

    val clientDistributionName = getClientDistributionName(clientBuildVersion)

    val clientBuildNumber = if (isSnapshot && config.downloadLatestBuildFromCDNForSnapshotHost) getLatestBuild(hostBuildNumber) else hostBuildNumber
    val clientDownloadUrl = "${config.clientDownloadUrl.toString().trimEnd('/')}/$clientDistributionName-$clientBuildNumber$platformSuffix"

    val jreDownloadUrl = if (jreBuildToDownload != null) {
      val platformString = when {
        SystemInfo.isLinux && CpuArch.isIntel64() -> "linux-x64"
        SystemInfo.isLinux && CpuArch.isArm64() -> "linux-aarch64"
        SystemInfo.isWindows && CpuArch.isIntel64() -> "windows-x64"
        SystemInfo.isWindows && CpuArch.isArm64() -> "windows-aarch64"
        SystemInfo.isMac && CpuArch.isIntel64() -> "osx-x64"
        SystemInfo.isMac && CpuArch.isArm64() -> "osx-aarch64"
        else -> error("Current platform is not supported")
      }

      val jreBuildParts = jreBuildToDownload.split("b")
      require(jreBuildParts.size == 2) { "jreBuild format should be like 12_3_45b6789.0: ${jreBuildToDownload}" }
      require(jreBuildParts[0].matches(Regex("^[0-9_.]+$"))) { "jreBuild format should be like 12_3_45b6789.0: ${jreBuildToDownload}" }
      require(jreBuildParts[1].matches(Regex("^[0-9.]+$"))) { "jreBuild format should be like 12_3_45b6789.0: ${jreBuildToDownload}" }

      /**
       * After upgrade to JRE 17 Jetbrains Runtime Team made a couple of incompatible changes:
       * 1. Java version began to contain dots in its version
       * 2. Root directory was renamed from 'jbr' to 'jbr_jcef_12.3.4b1235'
       *
       * We decided to maintain backward compatibility with old IDEs and
       * rename archives and root directories back to old format.
       */
      val jdkVersion = jreBuildParts[0].replace(".", "_")
      val jdkBuild = jreBuildParts[1]
      val jreDownloadUrl = "${config.jreDownloadUrl.toString().trimEnd('/')}/jbr_jcef-$jdkVersion-$platformString-b${jdkBuild}.tar.gz"

      jreDownloadUrl
    } else null

    val pgpPublicKeyUrl = if (unattendedMode) {
      RemoteDevSystemSettings.getPgpPublicKeyUrl().value
    } else null

    val sessionInfo = JetBrainsClientDownloadInfo(
      hostBuildNumber = hostBuildNumber,
      clientBuildNumber = clientBuildNumber, 
      compatibleClientUrl = clientDownloadUrl,
      compatibleJreUrl = jreDownloadUrl,
      downloadPgpPublicKeyUrl = pgpPublicKeyUrl
    )

    LOG.info("Generated session info: $sessionInfo")
    return sessionInfo
  }

  private fun getLatestBuild(hostBuildNumber: String): String {
    val majorVersion = hostBuildNumber.substringBefore('.')
    val latestBuildTxtFileName = "$majorVersion-LAST-BUILD.txt"
    val latestBuildTxtUri = "${config.clientDownloadUrl.toASCIIString().trimEnd('/')}/$latestBuildTxtFileName"

    val tempFile = Files.createTempFile(latestBuildTxtFileName, "")
    return try {
      downloadWithRetries(URI(latestBuildTxtUri), tempFile, EmptyProgressIndicator()).let {
        tempFile.readText().trim()
      }
    }
    finally {
      Files.deleteIfExists(tempFile)
    }
  }

  private val currentlyDownloading = ConcurrentHashMap<Path, CompletableFuture<Boolean>>()

  @ApiStatus.Experimental
  fun downloadClientAndJdk(clientBuildVersion: String,
                           progressIndicator: ProgressIndicator): ExtractedJetBrainsClientData? {
    ApplicationManager.getApplication().assertIsNonDispatchThread()

    val jdkBuildProgressIndicator = progressIndicator.createSubProgress(0.1)
    val jdkBuild = if (isClientWithBundledJre(clientBuildVersion)) {
      jdkBuildProgressIndicator.fraction = 1.0
      null
    } else {
      // Obsolete since 2022.3. Now the client has JRE bundled in
      LOG.info("Downloading Thin Client jdk-build.txt")
      jdkBuildProgressIndicator.text = RemoteDevUtilBundle.message("thinClientDownloader.checking")

      val clientDistributionName = getClientDistributionName(clientBuildVersion)
      val clientJdkDownloadUrl = "${config.clientDownloadUrl}$clientDistributionName-$clientBuildVersion-jdk-build.txt"
      LOG.info("Downloading from $clientJdkDownloadUrl")

      val tempFile = Files.createTempFile("jdk-build", "txt")
      try {
        downloadWithRetries(URI(clientJdkDownloadUrl), tempFile, EmptyProgressIndicator()).let {
          tempFile.readText()
        }
      }
      finally {
        Files.delete(tempFile)
      }
    }

    val sessionInfo = createSessionInfo(clientBuildVersion, jdkBuild, true)
    return downloadClientAndJdk(sessionInfo, progressIndicator.createSubProgress(0.9))
  }

  /**
   * @param clientBuildVersion format: 213.1337[.23]
   * @param jreBuild format: 11_0_11b1536.1
   * where 11_0_11 is jdk version, b1536.1 is the build version
   * @returns Pair(path/to/thin/client, path/to/jre)
   *
   * Update this method (any jdk-related stuff) together with:
   *  `org/jetbrains/intellij/build/impl/BundledJreManager.groovy`
   */
  fun downloadClientAndJdk(clientBuildVersion: String,
                           jreBuild: String?,
                           progressIndicator: ProgressIndicator): ExtractedJetBrainsClientData? {
    ApplicationManager.getApplication().assertIsNonDispatchThread()

    val sessionInfo = createSessionInfo(clientBuildVersion, jreBuild, true)
    return downloadClientAndJdk(sessionInfo, progressIndicator)
  }

  fun isClientDownloaded(
    clientBuildVersion: String,
  ): Boolean {
    val clientUrl = createSessionInfo(clientBuildVersion, null, true).compatibleClientUrl
    val tempDir = FileUtil.createTempDirectory("jb-cwm-dl", null).toPath()
    val guestData = DownloadableFileData.build(
      url = URI.create(clientUrl),
      tempDir = tempDir,
      cachesDir = config.clientCachesDir,
      includeInManifest = getJetBrainsClientManifestFilter(clientBuildVersion),
    )
    return isAlreadyDownloaded(guestData)
  }

  fun extractedClientData(clientBuildVersion: String): ExtractedJetBrainsClientData? {
    if (!isClientDownloaded(clientBuildVersion)) {
      return null
    }
    val clientUrl = createSessionInfo(clientBuildVersion, null, true).compatibleClientUrl
    val tempDir = FileUtil.createTempDirectory("jb-cwm-dl", null).toPath()
    val guestData = DownloadableFileData.build(
      url = URI.create(clientUrl),
      tempDir = tempDir,
      cachesDir = config.clientCachesDir,
      includeInManifest = getJetBrainsClientManifestFilter(clientBuildVersion),
    )
    return ExtractedJetBrainsClientData(clientDir = guestData.targetPath, jreDir = null, version = clientBuildVersion)
  }


  suspend fun downloadClientAndJdk(sessionInfoResponse: JetBrainsClientDownloadInfo): ExtractedJetBrainsClientData {
    return withProgressText(RemoteDevUtilBundle.message("launcher.get.client.info")) {
      coroutineToIndicator {
        downloadClientAndJdk(sessionInfoResponse, ProgressManager.getInstance().progressIndicator)
      }
    }
  }

  fun downloadClientAndJdk(sessionInfoResponse: JetBrainsClientDownloadInfo,
                           progressIndicator: ProgressIndicator): ExtractedJetBrainsClientData {
    ApplicationManager.getApplication().assertIsNonDispatchThread()

    val embeddedClientLauncher = createEmbeddedClientLauncherIfAvailable(sessionInfoResponse.clientBuildNumber)
    if (embeddedClientLauncher != null) {
      return ExtractedJetBrainsClientData(Path(PathManager.getHomePath()), null, sessionInfoResponse.clientBuildNumber)
    }

    val tempDir = FileUtil.createTempDirectory("jb-cwm-dl", null).toPath()
    LOG.info("Downloading Thin Client in $tempDir...")

    val clientUrl = URI(sessionInfoResponse.compatibleClientUrl)
    val guestData = DownloadableFileData.build(
      url = clientUrl,
      tempDir = tempDir,
      cachesDir = config.clientCachesDir,
      includeInManifest = getJetBrainsClientManifestFilter(sessionInfoResponse.clientBuildNumber),
    )

    val jdkUrl = sessionInfoResponse.compatibleJreUrl?.let { URI(it) }
    val jdkData = if (jdkUrl != null) {
      DownloadableFileData.build(
        url = jdkUrl,
        tempDir = tempDir,
        cachesDir = config.clientCachesDir,
        includeInManifest = cwmJbrManifestFilter,
      )
    }
    else null

    val dataList = listOfNotNull(jdkData, guestData)

    val activity: StructuredIdeActivity? = if (dataList.isEmpty()) null else RemoteDevStatisticsCollector.onGuestDownloadStarted()

    fun updateStateText() {
      val downloadList = dataList.filter { it.status.get() == DownloadableFileData.DownloadableFileState.Downloading }.joinToString(", ") { it.fileCaption }
      val extractList = dataList.filter { it.status.get() == DownloadableFileData.DownloadableFileState.Extracting }.joinToString(", ") { it.fileCaption }
      progressIndicator.text =
        if (downloadList.isNotBlank() && extractList.isNotBlank()) RemoteDevUtilBundle.message("thinClientDownloader.downloading.and.extracting", downloadList, extractList)
        else if (downloadList.isNotBlank()) RemoteDevUtilBundle.message("thinClientDownloader.downloading", downloadList)
        else if (extractList.isNotBlank()) RemoteDevUtilBundle.message("thinClientDownloader.extracting", extractList)
        else RemoteDevUtilBundle.message("thinClientDownloader.ready")
    }
    updateStateText()

    val dataProgressIndicators = MultipleSubProgressIndicator.create(progressIndicator, dataList.size)
    for ((index, data) in dataList.withIndex()) {
      // download
      val future = data.downloadFuture

      // Update only fraction via progress indicator API, text will be updated by updateStateText function
      val dataProgressIndicator = dataProgressIndicators[index]

      AppExecutorUtil.getAppScheduledExecutorService().execute {
        try {
          val existingDownloadFuture = synchronized(currentlyDownloading) {
            val existingDownloadInnerFuture = currentlyDownloading[data.targetPath]
            if (existingDownloadInnerFuture != null) {
              existingDownloadInnerFuture
            }
            else {
              currentlyDownloading[data.targetPath] = data.downloadFuture
              null
            }
          }

          // TODO: how to merge progress indicators in this case?
          if (existingDownloadFuture != null) {
            LOG.warn("Already downloading and extracting to ${data.targetPath}, will wait until download finished")
            existingDownloadFuture.whenComplete { res, ex ->
              if (ex != null) {
                future.completeExceptionally(ex)
              }
              else {
                future.complete(res)
              }
            }
            return@execute
          }

          if (isAlreadyDownloaded(data)) {
            LOG.info("Already downloaded and extracted ${data.fileCaption} to ${data.targetPath}")
            data.status.set(DownloadableFileData.DownloadableFileState.Done)
            dataProgressIndicator.fraction = 1.0
            updateStateText()
            future.complete(true)
            return@execute
          }

          val downloadingDataProgressIndicator = dataProgressIndicator.createSubProgress(0.5)

          try {
            fun download(url: URI, path: Path) {
              downloadWithRetries(url, path, downloadingDataProgressIndicator)
            }

            download(data.url, data.archivePath)

            LOG.info("Signature verification is ${if (config.verifySignature) "ON" else "OFF"}")
            if (config.verifySignature) {
              val pgpKeyRingFile = Files.createTempFile(tempDir, "KEYS", "")
              download(URI(sessionInfoResponse.downloadPgpPublicKeyUrl ?: JetBrainsPgpConstants.JETBRAINS_DOWNLOADS_PGP_SUB_KEYS_URL), pgpKeyRingFile)

              val checksumPath = data.archivePath.addSuffix(SHA256_SUFFIX)
              val signaturePath = data.archivePath.addSuffix(SHA256_ASC_SUFFIX)

              download(data.url.addPathSuffix(SHA256_SUFFIX), checksumPath)
              download(data.url.addPathSuffix(SHA256_ASC_SUFFIX), signaturePath)

              val pgpVerifier = PgpSignaturesVerifier(object : PgpSignaturesVerifierLogger {
                override fun info(message: String) {
                  LOG.info("Verifying ${data.url} PGP signature: $message")
                }
              })

              LOG.info("Running checksum signature verifier for ${data.archivePath}")
              Sha256ChecksumSignatureVerifier(pgpVerifier).verifyChecksumAndSignature(
                file = data.archivePath,
                detachedSignatureFile = signaturePath,
                checksumFile = checksumPath,
                expectedFileName = data.url.path.substringAfterLast('/'),
                untrustedPublicKeyRing = ByteArrayInputStream(Files.readAllBytes(pgpKeyRingFile)),
                trustedMasterKey = ByteArrayInputStream(JETBRAINS_DOWNLOADS_PGP_MASTER_PUBLIC_KEY.toByteArray()),
              )
              LOG.info("Signature verified for ${data.archivePath}")
            }
          }
          catch (ex: IOException) {
            future.completeExceptionally(ex)
            LOG.warn(ex)
            return@execute
          }

          // extract
          dataProgressIndicator.fraction = 0.75
          data.status.set(DownloadableFileData.DownloadableFileState.Extracting)
          updateStateText()

          // downloading a .zip file will get a VirtualFile with a path of `jar://C:/Users/ivan.pashchenko/AppData/Local/Temp/CodeWithMeGuest-212.2033-windows-x64.zip!/`
          // see FileDownloaderImpl.findVirtualFiles making a call to VfsUtil.getUrlForLibraryRoot(ioFile)
          val archivePath = data.archivePath

          LOG.info("Extracting $archivePath to ${data.targetPath}...")
          FileUtil.delete(data.targetPath)

          require(data.targetPath.notExists()) { "Target path \"${data.targetPath}\" for $archivePath already exists" }
          FileManifestUtil.decompressWithManifest(
            archiveFile = archivePath,
            targetDir = data.targetPath,
            includeModifiedDate = config.modifiedDateInManifestIncluded,
            includeInManifest = data.includeInManifest,
            progress = dataProgressIndicator.createSubProgress(0.25)
          )

          require(FileManifestUtil.isUpToDate(data.targetPath, config.modifiedDateInManifestIncluded, data.includeInManifest)) {
            "Manifest verification failed for archive: $archivePath -> ${data.targetPath}"
          }

          dataProgressIndicator.fraction = 1.0
          data.status.set(DownloadableFileData.DownloadableFileState.Done)
          updateStateText()

          Files.delete(archivePath)
          future.complete(true)
        }
        catch (e: Throwable) {
          future.completeExceptionally(e)
          LOG.warn(e)
        }
        finally {
          synchronized(currentlyDownloading) {
            currentlyDownloading.remove(data.targetPath)
          }
        }
      }
    }

    try {
      val guestSucceeded = guestData.downloadFuture.get()
      val jdkSucceeded = jdkData?.downloadFuture?.get() ?: true

      if (!guestSucceeded || !jdkSucceeded) error("Guest or jdk was not downloaded")

      LOG.info("Download of guest and jdk succeeded")
      return ExtractedJetBrainsClientData(clientDir = guestData.targetPath, jreDir = jdkData?.targetPath, version = sessionInfoResponse.clientBuildNumber)
    }
    catch(e: ProcessCanceledException) {
      LOG.info("Download was canceled")
      throw e
    }
    catch (e: Throwable) {
      RemoteDevStatisticsCollector.onGuestDownloadFinished(activity, isSucceeded = false)
      LOG.warn(e)
      if (e is ExecutionException) {
        e.cause?.let { throw it }
      }
      throw e
    }
  }

  internal fun createEmbeddedClientLauncherIfAvailable(expectedClientBuildNumber: String): EmbeddedClientLauncher? {
    if (Registry.`is`("rdct.use.embedded.client") || Registry.`is`("rdct.always.use.embedded.client")) {
      val hostBuildNumberString = BuildNumber.fromStringOrNull(expectedClientBuildNumber)?.withoutProductCode()
      val currentIdeBuildNumber = ApplicationInfo.getInstance().build.withoutProductCode()
      LOG.debug("Host build number: $hostBuildNumberString, current IDE build number: $currentIdeBuildNumber")
      if (hostBuildNumberString == currentIdeBuildNumber || Registry.`is`("rdct.always.use.embedded.client")) {
        val embeddedClientLauncher = EmbeddedClientLauncher.create()
        if (embeddedClientLauncher != null) {
          LOG.debug("Embedded client is available")
          return embeddedClientLauncher
        }
        else {
          LOG.debug("Embedded client isn't available in the current IDE installation")
        }
      }
    }
    return null
  }

  private fun isAlreadyDownloaded(fileData: DownloadableFileData): Boolean {
    val extractDirectory = FileManifestUtil.getExtractDirectory(fileData.targetPath, config.modifiedDateInManifestIncluded, fileData.includeInManifest)
    return extractDirectory.isUpToDate && !fileData.targetPath.fileName.toString().contains("SNAPSHOT")
  }

  private fun downloadWithRetries(url: URI, path: Path, progressIndicator: ProgressIndicator) {
    ApplicationManager.getApplication().assertIsNonDispatchThread()

    @Suppress("LocalVariableName")
    val MAX_ATTEMPTS = 5

    @Suppress("LocalVariableName")
    val BACKOFF_INITIAL_DELAY_MS = 500L

    var delayMs = BACKOFF_INITIAL_DELAY_MS

    for (i in 1..MAX_ATTEMPTS) {
      try {
        LOG.info("Downloading from $url to ${path.absolutePathString()}, attempt $i of $MAX_ATTEMPTS")

        when (url.scheme) {
          "http", "https" -> {
            HttpRequests.request(url.toString()).saveToFile(path, progressIndicator, true)
            progressIndicator.text2 = ""
          }
          "file" -> {
            val source = url.toPath()
            if (source.isDirectory()) {
              error("Downloading a directory is not supported. Source: $url, destination: ${path.absolutePathString()}")
            }

            Files.copy(source, path, StandardCopyOption.REPLACE_EXISTING)
          }
          else -> {
            error("scheme ${url.scheme} is not supported")
          }
        }

        LOG.info("Download from $url to ${path.absolutePathString()} succeeded on attempt $i of $MAX_ATTEMPTS")
        return
      }
      catch (e: Throwable) {
        if (e is ControlFlowException) throw e

        if (e is HttpStatusException) {
          if (e.statusCode in 400..499) {
            LOG.warn("Received ${e.statusCode} with message ${e.message}, will not retry")
            throw e
          }
        }

        if (i < MAX_ATTEMPTS) {
          LOG.warn("Attempt $i of $MAX_ATTEMPTS to download from $url to ${path.absolutePathString()} failed, retrying in $delayMs ms", e)
          Thread.sleep(delayMs)
          delayMs = (delayMs * 1.5).toLong()
        } else {
          LOG.warn("Failed to download from $url to ${path.absolutePathString()} in $MAX_ATTEMPTS attempts", e)
          throw e
        }
      }
    }
  }

  private fun findCwmGuestHome(guestRoot: Path): Path {
    // maxDepth 2 for macOS's .app/Contents
    Files.walk(guestRoot, 2).use {
      for (dir in it) {
        if (dir.resolve("bin").exists() && dir.resolve("lib").exists()) {
          return dir
        }
      }
    }

    error("JetBrains Client home is not found under $guestRoot")
  }

  private fun findLauncher(guestRoot: Path, launcherNames: List<String>): JetBrainsClientLauncherData {
    val launcher = launcherNames.firstNotNullOfOrNull {
      val launcherRelative = Path.of("bin", it)
      val launcher = findLauncher(guestRoot, launcherRelative)
      launcher?.let {
        JetBrainsClientLauncherData(launcher, listOf(launcher.toString()))
      }
    }

    return launcher ?: error("Could not find launchers (${launcherNames.joinToString { "'$it'" }}) under $guestRoot")
  }

  private fun findLauncher(guestRoot: Path, launcherName: Path): Path? {
    // maxDepth 2 for macOS's .app/Contents
    Files.walk(guestRoot, 2).use {
      for (dir in it) {
        val candidate = dir.resolve(launcherName)
        if (candidate.exists()) {
          return candidate
        }
      }
    }

    return null
  }

  private fun findLauncherUnderCwmGuestRoot(guestRoot: Path): JetBrainsClientLauncherData {
    when {
      SystemInfo.isWindows -> {
        val batchLaunchers = listOf("intellij_client.bat", "jetbrains_client.bat")
        val exeLaunchers = listOf("jetbrains_client64.exe", "cwm_guest64.exe", "intellij_client64.exe")
        val eligibleLaunchers = if (Registry.`is`("com.jetbrains.gateway.client.use.batch.launcher", false))
          batchLaunchers
        else
          exeLaunchers + batchLaunchers
        return findLauncher(guestRoot, eligibleLaunchers)
      }

      SystemInfo.isUnix -> {
        if (SystemInfo.isMac) {
          val app = guestRoot.toFile().listFiles { file -> file.name.endsWith(".app") && file.isDirectory }!!.singleOrNull()
          if (app != null) {
            return createLauncherDataForMacOs(app.toPath())
          }
        }

        val shLauncherNames = listOf("jetbrains_client.sh", "cwm_guest.sh", "intellij_client.sh")
        return findLauncher(guestRoot, shLauncherNames)
      }

      else -> error("Unsupported OS: ${SystemInfo.OS_NAME}")
    }
  }

  internal fun createLauncherDataForMacOs(app: Path) =
    JetBrainsClientLauncherData(app, listOf("open", "-n", "-W", "-a", app.pathString, "--args"))

  /**
   * Launches client and returns process's lifetime (which will be terminated on process exit)
   */
  fun runCwmGuestProcessFromDownload(
    lifetime: Lifetime,
    url: String,
    extractedJetBrainsClientData: ExtractedJetBrainsClientData
  ): Lifetime {
    if (extractedJetBrainsClientData.clientDir == Path(PathManager.getHomePath())) {
      //todo: refactor this code to generalize ExtractedJetBrainsClientData and pass EmbeddedClientLauncher instance here explicitly
      return EmbeddedClientLauncher.create()!!.launch(url, lifetime, NotificationBasedEmbeddedClientErrorReporter(null))
    }
    
    val launcherData = findLauncherUnderCwmGuestRoot(extractedJetBrainsClientData.clientDir)

    if (extractedJetBrainsClientData.jreDir != null) {
      createSymlinkToJdkFromGuest(extractedJetBrainsClientData.clientDir, extractedJetBrainsClientData.jreDir)
    }

    // Update mtime on JRE & CWM Guest roots. The cleanup process will use it later.
    if (config.clientVersionManagementEnabled) {
      listOfNotNull(extractedJetBrainsClientData.clientDir, extractedJetBrainsClientData.jreDir).forEach { path ->
        Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis()))
      }
    }

    return runJetBrainsClientProcess(launcherData, 
                                     workingDirectory = extractedJetBrainsClientData.clientDir,
                                     clientVersion = extractedJetBrainsClientData.version, 
                                     url, lifetime)
  }

  internal fun runJetBrainsClientProcess(launcherData: JetBrainsClientLauncherData,
                                         workingDirectory: Path,
                                         clientVersion: String,
                                         url: String,
                                         lifetime: Lifetime): Lifetime {
    return runJetBrainsClientProcess(launcherData, workingDirectory, clientVersion, url, emptyList(),  lifetime)
  }

  internal fun runJetBrainsClientProcess(launcherData: JetBrainsClientLauncherData,
                                         workingDirectory: Path,
                                         clientVersion: String,
                                         url: String,
                                         extraArguments: List<String>,
                                         lifetime: Lifetime): Lifetime {
    val parameters = listOf("thinClient", url) + extraArguments
    val processLifetimeDef = lifetime.createNested()

    val vmOptionsFile = if (SystemInfoRt.isMac) {
      // macOS stores vmoptions file inside .app file – we can't edit it
      Paths.get(
        PathManager.getDefaultConfigPathFor(PlatformUtils.JETBRAINS_CLIENT_PREFIX + clientVersion),
        "jetbrains_client.vmoptions"
      )
    } else if (SystemInfoRt.isWindows) launcherData.executable.resolveSibling("jetbrains_client64.exe.vmoptions")
    else launcherData.executable.resolveSibling("jetbrains_client64.vmoptions")
    service<JetBrainsClientDownloaderConfigurationProvider>().patchVmOptions(vmOptionsFile, URI(url))

    val clientEnvironment = mutableMapOf<String, String>()
    val separateConfigOption = ClientVersionUtil.computeSeparateConfigEnvVariableValue(clientVersion)
    if (separateConfigOption != null) {
      clientEnvironment["JBC_SEPARATE_CONFIG"] = separateConfigOption
    }

    if (SystemInfo.isWindows) {
      val hProcess = WindowsFileUtil.windowsCreateProcess(
        executable = launcherData.executable,
        workingDirectory = workingDirectory,
        parameters = parameters,
        environment = clientEnvironment
      )

      @Suppress("LocalVariableName")
      val STILL_ACTIVE = 259

      application.executeOnPooledThread {
        val exitCode = IntByReference(STILL_ACTIVE)
        while (exitCode.value == STILL_ACTIVE) {
          Kernel32.INSTANCE.GetExitCodeProcess(hProcess, exitCode)
          Thread.sleep(1000)
        }
        processLifetimeDef.terminate()
      }

      lifetime.onTerminationOrNow {
        val exitCode = IntByReference(WinBase.INFINITE)
        Kernel32.INSTANCE.GetExitCodeProcess(hProcess, exitCode)

        if (exitCode.value == STILL_ACTIVE)
          LOG.info("Terminating cwm guest process")
        else return@onTerminationOrNow

        if (!Kernel32.INSTANCE.TerminateProcess(hProcess, 1)) {
          val error = Kernel32.INSTANCE.GetLastError()
          val hResult = WinNT.HRESULT(error)
          LOG.error("Failed to terminate cwm guest process, HRESULT=${"0x%x".format(hResult)}")
        }
      }
    }
    else {
      // Mac gets multiple start attempts because starting it fails occasionally (CWM-2244, CWM-1733)
      var attemptCount = if (SystemInfo.isMac) 5 else 1
      var lastProcessStartTime: Long

      fun doRunProcess() {
        val commandLine = GeneralCommandLine(launcherData.commandLine + parameters)
          .withEnvironment(clientEnvironment)

        config.modifyClientCommandLine(commandLine)

        LOG.info("Starting JetBrains Client process (attempts left: $attemptCount): ${commandLine}")

        attemptCount--
        lastProcessStartTime = System.currentTimeMillis()

        val processHandler = object : OSProcessHandler(commandLine) {
          override fun readerOptions(): BaseOutputReader.Options = BaseOutputReader.Options.forMostlySilentProcess()
        }

        val listener = object : ProcessAdapter() {
          override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
            super.onTextAvailable(event, outputType)
            LOG.info("GUEST OUTPUT: ${event.text}")
          }

          override fun processTerminated(event: ProcessEvent) {
            super.processTerminated(event)
            LOG.info("Guest process terminated, exit code " + event.exitCode)

            if (event.exitCode == 0) {
              application.invokeLater {
                processLifetimeDef.terminate()
              }
            } else {
              // if process exited abnormally but took longer than 10 seconds, it's likely to be an issue with connection instead of Mac-specific bug
              if ((System.currentTimeMillis() - lastProcessStartTime) < 10_000 && lifetime.isAlive) {
                if (attemptCount > 0) {
                  LOG.info("Previous attempt to start guest process failed, will try again in one second")
                  EdtScheduler.getInstance().schedule(1.seconds, ModalityState.any()) { doRunProcess() }
                }
                else {
                  LOG.warn("Running client process failed after specified number of attempts")
                  application.invokeLater {
                    processLifetimeDef.terminate()
                  }
                }
              }
            }
          }
        }

        processHandler.addProcessListener(listener)
        processHandler.startNotify()
        config.clientLaunched.fire()

        lifetime.onTerminationOrNow {
          processHandler.process.children().forEach {
            it.destroyForcibly()
          }
          processHandler.process.destroyForcibly()
        }
      }

      doRunProcess()
    }

    return processLifetimeDef.lifetime
  }

  fun createSymlinkToJdkFromGuest(guestRoot: Path, jdkRoot: Path): Path {
    val linkTarget = getJbrDirectory(jdkRoot)

    val guestHome = findCwmGuestHome(guestRoot)
    val link = guestHome / "jbr"
    createSymlink(link, linkTarget)
    return link
  }

  private fun createSymlink(link: Path, target: Path) {
    val targetRealPath = target.toRealPath()
    val linkExists = true
    val linkRealPath = if (link.exists(LinkOption.NOFOLLOW_LINKS)) link.toRealPath() else null
    val isSymlink = FileSystemUtil.getAttributes(link.toFile())?.isSymLink == true

    LOG.info("$link: exists=$linkExists, realPath=$linkRealPath, isSymlink=$isSymlink")
    if (linkExists && isSymlink && linkRealPath == targetRealPath) {
      LOG.info("Symlink/junction '$link' is UP-TO-DATE and points to '$target'")
    }
    else {
      FileUtil.deleteWithRenamingIfExists(link)

      LOG.info("Creating symlink/junction '$link' -> '$target'")

      try {
        if (SystemInfo.isWindows) {
          WindowsFileUtil.createJunction(junctionFile = link, targetFile = target.absolute())
        }
        else {
          Files.createSymbolicLink(link, target.absolute())
        }
      }
      catch (e: IOException) {
        if (link.exists() && link.toRealPath() == targetRealPath) {
          LOG.warn("Creating symlink/junction to already existing target. '$link' -> '$target'")
        }
        else {
          throw e
        }
      }
      try {
        val linkRealPath2 = link.toRealPath()
        if (linkRealPath2 != targetRealPath) {
          LOG.error("Symlink/junction '$link' should point to '$targetRealPath', but points to '$linkRealPath2' instead")
        }
      }
      catch (e: Throwable) {
        LOG.error(e)
        throw e
      }
    }
  }

  private fun getJbrDirectory(root: Path): Path =
    tryGetMacOsJbrDirectory(root) ?: tryGetJdkRoot(root) ?: error("Unable to detect jdk content directory in path: '$root'")


  private fun tryGetJdkRoot(jdkDownload: Path): Path? {
    jdkDownload.toFile().walk(FileWalkDirection.TOP_DOWN).forEach { file ->
      if (File(file, "bin").isDirectory && File(file, "lib").isDirectory) {
        return file.toPath()
      }
    }

    return null
  }

  private fun tryGetMacOsJbrDirectory(root: Path): Path? {
    if (!SystemInfo.isMac) {
      return null
    }

    val jbrDirectory = root.listDirectoryEntries().find { it.nameWithoutExtension.startsWith("jbr") }

    LOG.debug { "JBR directory: $jbrDirectory" }
    return jbrDirectory
  }

  fun versionsMatch(hostBuildNumberString: String, localBuildNumberString: String): Boolean {
    try {
      val hostBuildNumber = BuildNumber.fromString(hostBuildNumberString)!!
      val localBuildNumber = BuildNumber.fromString(localBuildNumberString)!!

      // Any guest in that branch compatible with SNAPSHOT version (it's used by IDEA developers mostly)
      if ((localBuildNumber.isSnapshot || hostBuildNumber.isSnapshot) && hostBuildNumber.baselineVersion == localBuildNumber.baselineVersion) {
        return true
      }

      return hostBuildNumber.asStringWithoutProductCode() == localBuildNumber.asStringWithoutProductCode()
    }
    catch (t: Throwable) {
      LOG.error("Error comparing versions $hostBuildNumberString and $localBuildNumberString: ${t.message}", t)
      return false
    }
  }

  private fun Path.addSuffix(suffix: String) = resolveSibling(fileName.toString() + suffix)

  private const val SHA256_SUFFIX = ".sha256"
  private const val SHA256_ASC_SUFFIX = ".sha256.asc"

  private val urlAllowedChars = Regex("^[._\\-a-zA-Z0-9:/]+$")
  fun isValidDownloadUrl(url: String): Boolean {
    return urlAllowedChars.matches(url) && !url.contains("..")
  }

  private class MultipleSubProgressIndicator(parent: ProgressIndicator,
                                             private val onFractionChange: () -> Unit) : SubProgressIndicatorBase(parent) {

    companion object {
      fun create(parent: ProgressIndicator, count: Int): List<MultipleSubProgressIndicator> {
        val result = mutableListOf<MultipleSubProgressIndicator>()
        val parentBaseFraction = parent.fraction

        for (i in 0..count) {
          val element = MultipleSubProgressIndicator(parent) {
            val subFraction = result.sumOf { it.subFraction }
            parent.fraction = min(parentBaseFraction + subFraction * (1.0 / count), 1.0)
          }
          result.add(element)
        }
        return result
      }
    }

    private var subFraction = 0.0

    override fun getFraction() = subFraction
    override fun setFraction(fraction: Double) {
      subFraction = fraction
      onFractionChange()
    }
  }
}

data class JetBrainsClientLauncherData(
  val executable: Path,
  val commandLine: List<String>
)