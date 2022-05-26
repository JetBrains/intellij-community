package com.intellij.remoteDev.downloader

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileSystemUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.remoteDev.RemoteDevUtilBundle
import com.intellij.remoteDev.connection.CodeWithMeSessionInfoProvider
import com.intellij.remoteDev.connection.StunTurnServerInfo
import com.intellij.remoteDev.util.*
import com.intellij.util.application
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtScheduledExecutorService
import com.intellij.util.io.*
import com.intellij.util.io.HttpRequests.HttpStatusException
import com.intellij.util.system.CpuArch
import com.intellij.util.text.VersionComparatorUtil
import com.jetbrains.infra.pgpVerifier.JetBrainsPgpConstants
import com.jetbrains.infra.pgpVerifier.JetBrainsPgpConstants.JETBRAINS_DOWNLOADS_PGP_MASTER_PUBLIC_KEY
import com.jetbrains.infra.pgpVerifier.PgpSignaturesVerifier
import com.jetbrains.infra.pgpVerifier.PgpSignaturesVerifierLogger
import com.jetbrains.infra.pgpVerifier.Sha256ChecksumSignatureVerifier
import com.jetbrains.rd.util.lifetime.Lifetime
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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.io.path.*
import kotlin.math.min

@ApiStatus.Experimental
object CodeWithMeClientDownloader {

  private val LOG = logger<CodeWithMeClientDownloader>()

  private const val extractDirSuffix = "-ide"

  private val config get () = service<JetBrainsClientDownloaderConfigurationProvider>()

  private fun isJbrSymlink(file: Path): Boolean = file.name == "jbr" && isSymlink(file)
  private fun isSymlink(file: Path): Boolean = FileSystemUtil.getAttributes(file.toFile())?.isSymLink == true

  val cwmGuestManifestFilter: (Path) -> Boolean = { !isJbrSymlink(it) && (!it.isDirectory() || isSymlink(it)) }
  val cwmJbrManifestFilter: (Path) -> Boolean = { !it.isDirectory() || isSymlink(it) }

  private data class DownloadableFileData(
    val fileName: String,
    val url: URI,
    val archivePath: Path,
    val targetPath: Path,
    val includeInManifest: (Path) -> Boolean,
    val downloadFuture: CompletableFuture<Boolean> = CompletableFuture(),
    var status: DownloadableFileState = DownloadableFileState.Downloading
  )

  private enum class DownloadableFileState {
    Downloading,
    Extracting,
    Done
  }

  val buildNumberRegex = Regex("""[0-9]{3}\.(([0-9]+(\.[0-9]+)?)|SNAPSHOT)""")

  private fun getClientDistributionName(clientBuildVersion: String) = when {
    VersionComparatorUtil.compare(clientBuildVersion, "211.6167") < 0 -> "IntelliJClient"
    VersionComparatorUtil.compare(clientBuildVersion, "213.5318") < 0 -> "CodeWithMeGuest"
    else -> "JetBrainsClient"
  }

  fun createSessionInfo(clientBuildVersion: String, jreBuild: String, unattendedMode: Boolean): CodeWithMeSessionInfoProvider {
    if ("SNAPSHOT" in clientBuildVersion) {
      LOG.warn(
        "Thin client download from sources may result in failure due to different sources on host and client, don't forget to update your locally built archive")
    }

    val hostBuildNumber = buildNumberRegex.find(clientBuildVersion)!!.value
    val platformSuffix = when {
      SystemInfo.isLinux -> "-no-jbr.tar.gz"
      SystemInfo.isWindows -> ".win.zip"
      SystemInfo.isMac && CpuArch.isIntel64() -> "-no-jdk.sit"
      SystemInfo.isMac && CpuArch.isArm64() -> "-no-jdk-aarch64.sit"
      else -> error("Current platform is not supported")
    }

    val clientDistributionName = getClientDistributionName(clientBuildVersion)

    val clientDownloadUrl = "${config.clientDownloadLocation}$clientDistributionName-$hostBuildNumber$platformSuffix"

    val platformString = when {
      SystemInfo.isLinux -> "linux-x64"
      SystemInfo.isWindows -> "windows-x64"
      SystemInfo.isMac && CpuArch.isIntel64() -> "osx-x64"
      SystemInfo.isMac && CpuArch.isArm64() -> "osx-aarch64"
      else -> error("Current platform is not supported")
    }

    val jreBuildParts = jreBuild.split("b")
    require(jreBuildParts.size == 2) { "jreBuild format should be like 12_3_45b6789.0" }
    require(jreBuildParts[0].matches(Regex("^[0-9_]+$"))) { "jreBuild format should be like 12_3_45b6789.0" }
    require(jreBuildParts[1].matches(Regex("^[0-9.]+$"))) { "jreBuild format should be like 12_3_45b6789.0" }

    val jdkVersion = jreBuildParts[0]
    val jdkBuild = jreBuildParts[1]
    val jreDownloadUrl = "${config.jreDownloadLocation}jbr_jcef-$jdkVersion-$platformString-b${jdkBuild}.tar.gz"

    val clientName = "$clientDistributionName-$hostBuildNumber"
    val jreName = jreDownloadUrl.substringAfterLast('/').removeSuffix(".tar.gz")

    val sessionInfo = object : CodeWithMeSessionInfoProvider {
      override val hostBuildNumber = hostBuildNumber
      override val compatibleClientName = clientName
      override val compatibleClientUrl = clientDownloadUrl
      override val compatibleJreName = jreName
      override val isUnattendedMode = unattendedMode
      override val compatibleJreUrl = jreDownloadUrl
      override val hostFeaturesToEnable: Set<String>? = null
      override val stunTurnServers: List<StunTurnServerInfo>? = null
      override val downloadPgpPublicKeyUrl: String? = null
    }

    LOG.info("Generated session info: $sessionInfo")
    return sessionInfo
  }

  private val currentlyDownloading = ConcurrentHashMap<Path, CompletableFuture<Boolean>>()

  @ApiStatus.Experimental
  fun downloadClientAndJdk(clientBuildVersion: String,
                           progressIndicator: ProgressIndicator): Pair<Path, Path>? {
    require(application.isUnitTestMode || !application.isDispatchThread) { "This method should not be called on UI thread" }

    LOG.info("Downloading Thin Client jdk-build.txt")
    val jdkBuildProgressIndicator = progressIndicator.createSubProgress(0.1)
    jdkBuildProgressIndicator.text = RemoteDevUtilBundle.message("thinClientDownloader.checking")

    val clientDistributionName = getClientDistributionName(clientBuildVersion)
    val clientJdkDownloadUrl = "${config.clientDownloadLocation}$clientDistributionName-$clientBuildVersion-jdk-build.txt"
    LOG.info("Downloading from $clientJdkDownloadUrl")

    val tempFile = Files.createTempFile("jdk-build", "txt")
    val jdkBuild = try {
      downloadWithRetries(URI(clientJdkDownloadUrl), tempFile, EmptyProgressIndicator()).let {
        tempFile.readText()
      }
    } finally {
      Files.delete(tempFile)
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
   *  `setupJdk.gradle`
   *  `org/jetbrains/intellij/build/impl/BundledJreManager.groovy`
   */
  fun downloadClientAndJdk(clientBuildVersion: String,
                           jreBuild: String,
                           progressIndicator: ProgressIndicator): Pair<Path, Path>? {
    require(application.isUnitTestMode || !application.isDispatchThread) { "This method should not be called on UI thread" }

    val sessionInfo = createSessionInfo(clientBuildVersion, jreBuild, true)
    return downloadClientAndJdk(sessionInfo, progressIndicator)
  }

  /**
   * @returns Pair(path/to/thin/client, path/to/jre)
   */
  fun downloadClientAndJdk(sessionInfoResponse: CodeWithMeSessionInfoProvider,
                           progressIndicator: ProgressIndicator): Pair<Path, Path>? {
    require(application.isUnitTestMode || !application.isDispatchThread) { "This method should not be called on UI thread" }

    val tempDir = FileUtil.createTempDirectory("jb-cwm-dl", null).toPath()
    LOG.info("Downloading Thin Client in $tempDir...")

    fun archiveExtensionFromUrl(url: String) = when {
      url.endsWith(".zip") -> "zip"
      url.endsWith(".sit") -> "sit"
      url.endsWith(".tar.gz") -> "tar.gz"
      else -> error("Don't know how to extract archive downloaded from url $url")
    }

    val guestName = sessionInfoResponse.compatibleClientName
    val guestFileName = "$guestName.${archiveExtensionFromUrl(sessionInfoResponse.compatibleClientUrl)}"
    val guestData = DownloadableFileData(
      fileName = guestFileName,
      url = URI(sessionInfoResponse.compatibleClientUrl),
      archivePath = tempDir.resolve(guestFileName),
      targetPath = config.clientCachesDir / (guestName + extractDirSuffix),
      includeInManifest = cwmGuestManifestFilter
    )

    val jdkFullName = sessionInfoResponse.compatibleJreName
    val jdkFileName = "$jdkFullName.${archiveExtensionFromUrl(sessionInfoResponse.compatibleJreUrl)}"
    val jdkData = DownloadableFileData(
      fileName = jdkFileName,
      url = URI(sessionInfoResponse.compatibleJreUrl),
      archivePath = tempDir.resolve(jdkFileName),
      targetPath = config.clientCachesDir / (jdkFullName + extractDirSuffix),
      includeInManifest = cwmJbrManifestFilter
    )

    val dataList = arrayOf(jdkData, guestData)

    val activity: StructuredIdeActivity? =
      if (dataList.isNotEmpty()) RemoteDevStatisticsCollector.onGuestDownloadStarted()
      else null

    fun updateStateText() {
      val downloadList = dataList.filter { it.status == DownloadableFileState.Downloading }.joinToString(", ") { it.fileName }
      val extractList = dataList.filter { it.status == DownloadableFileState.Extracting }.joinToString(", ") { it.fileName }
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
            LOG.info("Already downloaded and extracted ${data.fileName} to ${data.targetPath}")
            data.status = DownloadableFileState.Done
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
            future.complete(false)
            LOG.warn(ex)
            return@execute
          }

          // extract
          dataProgressIndicator.fraction = 0.75
          data.status = DownloadableFileState.Extracting
          updateStateText()

          // downloading a .zip file will get a VirtualFile with a path of `jar://C:/Users/ivan.pashchenko/AppData/Local/Temp/CodeWithMeGuest-212.2033-windows-x64.zip!/`
          // see FileDownloaderImpl.findVirtualFiles making a call to VfsUtil.getUrlForLibraryRoot(ioFile)
          val archivePath = data.archivePath

          LOG.info("Extracting $archivePath to ${data.targetPath}...")
          FileUtil.delete(data.targetPath)

          require(data.targetPath.notExists()) { "Target path \"${data.targetPath}\" for $archivePath already exists" }
          FileManifestUtil.decompressWithManifest(archivePath, data.targetPath, data.includeInManifest)

          require(FileManifestUtil.isUpToDate(data.targetPath, data.includeInManifest)) {
            "Manifest verification failed for archive: $archivePath -> ${data.targetPath}"
          }

          dataProgressIndicator.fraction = 1.0
          data.status = DownloadableFileState.Done
          updateStateText()

          Files.delete(archivePath)
          future.complete(true)
        }
        catch (e: Throwable) {
          future.complete(false)
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
      val jdkSucceeded = jdkData.downloadFuture.get()

      if (guestSucceeded && jdkSucceeded) {
        RemoteDevStatisticsCollector.onGuestDownloadFinished(activity, isSucceeded = true)
        LOG.info("Download of guest and jdk succeeded")
        return guestData.targetPath to jdkData.targetPath
      }
      else {
        LOG.warn("Some of downloads failed: guestSucceeded=$guestSucceeded, jdkSucceeded=$jdkSucceeded")
        RemoteDevStatisticsCollector.onGuestDownloadFinished(activity, isSucceeded = false)
        return null
      }
    }
    catch(e: ProcessCanceledException) {
      LOG.info("Download was canceled")
      return null
    }
    catch (e: Throwable) {
      LOG.warn(e)
      return null
    }
  }

  private fun isAlreadyDownloaded(fileData: DownloadableFileData): Boolean {
    val extractDirectory = FileManifestUtil.getExtractDirectory(fileData.targetPath, fileData.includeInManifest)
    return extractDirectory.isUpToDate && !fileData.targetPath.fileName.toString().contains("SNAPSHOT")
  }

  private fun downloadWithRetries(url: URI, path: Path, progressIndicator: ProgressIndicator) {
    require(application.isUnitTestMode || !application.isDispatchThread) { "This method should not be called on UI thread" }

    val MAX_ATTEMPTS = 5
    val BACKOFF_INITIAL_DELAY_MS = 500L

    var delayMs = BACKOFF_INITIAL_DELAY_MS

    for (i in 1..MAX_ATTEMPTS) {
      try {
        LOG.info("Downloading from $url to ${path.absolutePathString()}, attempt $i of $MAX_ATTEMPTS")

        HttpRequests.request(url.toString()).saveToFile(path, progressIndicator)

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
    // maxDepth 2 for Mac OS's .app/Contents
    Files.walk(guestRoot, 2).use {
      for (dir in it) {
        if (dir.resolve("bin").exists() && dir.resolve("lib").exists()) {
          return dir
        }
      }
    }

    error("JetBrains Client home is not found under $guestRoot")
  }

  private fun findLauncher(guestRoot: Path, launcherNames: List<String>): Pair<Path, List<String>> {
    val launcher = launcherNames.firstNotNullOfOrNull {
      val launcherRelative = Path.of("bin", it)
      val launcher = findLauncher(guestRoot, launcherRelative)
      launcher?.let {
        launcher to listOf(launcher.toString())
      }
    }

    return launcher ?: error("Could not find launchers (${launcherNames.joinToString { "'$it'" }}) under $guestRoot")
  }

  private fun findLauncher(guestRoot: Path, launcherName: Path): Path? {
    // maxDepth 2 for Mac OS's .app/Contents
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

  private fun findLauncherUnderCwmGuestRoot(guestRoot: Path): Pair<Path, List<String>> {
    when {
      SystemInfo.isWindows -> {
        val launcherNames = listOf("jetbrains_client64.exe", "cwm_guest64.exe", "intellij_client64.exe", "intellij_client.bat")
        return findLauncher(guestRoot, launcherNames)
      }

      SystemInfo.isUnix -> {
        if (SystemInfo.isMac) {
          val app = guestRoot.toFile().listFiles { file -> file.name.endsWith(".app") && file.isDirectory }!!.singleOrNull()
          if (app != null) {
            return app.toPath() to listOf("open", "-n", "-a", app.toString(), "--args")
          }
        }

        val shLauncherNames = listOf("jetbrains_client.sh", "cwm_guest.sh", "intellij_client.sh")
        return findLauncher(guestRoot, shLauncherNames)
      }

      else -> error("Unsupported OS: ${SystemInfo.OS_NAME}")
    }
  }

  /**
   * Launches client and returns process's lifetime (which will be terminated on process exit)
   */
  fun runCwmGuestProcessFromDownload(lifetime: Lifetime,
                                     url: String,
                                     guestRoot: Path,
                                     jdkRoot: Path): Lifetime {
    val (executable, fullLauncherCmd) = findLauncherUnderCwmGuestRoot(guestRoot)
    val guestHome = findCwmGuestHome(guestRoot)

    val linkTarget = if (SystemInfo.isMac) jdkRoot / "jbr" else detectTrueJdkRoot(jdkRoot)
    createSymlink(guestHome / "jbr", linkTarget)

    // Update mtime on JRE & CWM Guest roots. The cleanup process will use it later.
    listOf(guestRoot, jdkRoot).forEach { path ->
      Files.setLastModifiedTime(path, FileTime.fromMillis(System.currentTimeMillis()))
    }

    val parameters = listOf("thinClient", url)
    val processLifetimeDef = lifetime.createNested()

    val vmOptionsFile = executable.resolveSibling("jetbrains_client64.vmoptions")
    service<JetBrainsClientDownloaderConfigurationProvider>().patchVmOptions(vmOptionsFile)

    if (SystemInfo.isWindows) {
      val hProcess = WindowsFileUtil.windowsShellExecute(
        executable = executable,
        workingDirectory = guestRoot,
        parameters = parameters
      )

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
        val commandLine = GeneralCommandLine(fullLauncherCmd + parameters)
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

            // if process exited abnormally but took longer than 10 seconds, it's likely to be an issue with connection instead of Mac-specific bug
            if (event.exitCode != 0 && (System.currentTimeMillis() - lastProcessStartTime) < 10_000) {
              if (attemptCount > 0) {
                LOG.info("Previous attempt to start guest process failed, will try again in one second")
                EdtScheduledExecutorService.getInstance().schedule({ doRunProcess() }, ModalityState.any(), 1, TimeUnit.SECONDS)
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

  fun createSymlinkToJdkFromGuest(guestRoot: Path, jdkRoot: Path) {
    val linkTarget = if (SystemInfo.isMac) jdkRoot / "jbr" else detectTrueJdkRoot(jdkRoot)
    val guestHome = findCwmGuestHome(guestRoot)
    createSymlink(guestHome / "jbr", linkTarget)
  }

  private fun createSymlink(link: Path, target: Path) {
    val targetRealPath = target.toRealPath()
    if (link.exists() && link.toRealPath() == targetRealPath) {
      LOG.info("Symlink/junction '$link' is UP-TO-DATE and points to '$target'")
    }
    else {
      Files.deleteIfExists(link)

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
        val linkRealPath = link.toRealPath()
        if (linkRealPath != targetRealPath) {
          LOG.error("Symlink/junction '$link' should point to '$targetRealPath', but points to '$linkRealPath' instead")
        }
      }
      catch (e: Throwable) {
        LOG.error(e)
        throw e
      }
    }
  }

  private fun detectTrueJdkRoot(jdkDownload: Path): Path {
    jdkDownload.toFile().walk(FileWalkDirection.TOP_DOWN).forEach {
      if (File(it, "bin").isDirectory && File(it, "lib").isDirectory) {
        return it.toPath()
      }
    }

    error("JDK root (bin/lib directories) was not found under $jdkDownload")
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