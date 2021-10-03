package com.intellij.remoteDev.downloader

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileSystemUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.application
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.remoteDev.RemoteDevUtilBundle
import com.intellij.util.concurrency.EdtScheduledExecutorService
import com.intellij.util.io.*
import com.intellij.remoteDev.connection.CodeWithMeSessionInfoProvider
import com.intellij.remoteDev.connection.StunTurnServerInfo
import com.intellij.remoteDev.util.*
import com.jetbrains.infra.pgpVerifier.JetBrainsPgpConstants
import com.jetbrains.infra.pgpVerifier.JetBrainsPgpConstants.JETBRAINS_DOWNLOADS_PGP_MASTER_PUBLIC_KEY
import com.jetbrains.infra.pgpVerifier.PgpSignaturesVerifier
import com.jetbrains.infra.pgpVerifier.PgpSignaturesVerifierLogger
import com.jetbrains.infra.pgpVerifier.Sha256ChecksumSignatureVerifier
import com.jetbrains.rd.util.lifetime.Lifetime
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import org.jetbrains.annotations.ApiStatus
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.io.path.*

object CodeWithMeClientDownloader {

  private val LOG = logger<CodeWithMeClientDownloader>()

  private const val extractDirSuffix = "-ide"

  const val gatewayTestsInstallersDirProperty = "intellij.cwm.tests.remoteDev.gateway.idea.tar.gz.dir"
  const val gatewayTestsX11DisplayProperty = "intellij.cwm.tests.remoteDev.gateway.x11.display"
  const val cwmTestsGuestCachesSystemProperty = "codeWithMe.tests.guest.caches.dir"
  const val DEFAULT_GUEST_CACHES_DIR_NAME = "CodeWithMeClientDist"

  private fun isJbrSymlink(file: Path): Boolean {
    return file.name == "jbr" && FileSystemUtil.getAttributes(file.toFile())?.isSymLink == true
  }

  val cwmGuestManifestFilter: (Path) -> Boolean = { !isJbrSymlink(it) && !it.isDirectory() }
  val cwmJbrManifestFilter: (Path) -> Boolean = { !it.isDirectory() }

  // todo: make it configurable.... for enterprise?
  private val DEFAULT_CWM_GUEST_DOWNLOAD_LOCATION = URI("https://cache-redirector.jetbrains.com/download.jetbrains.com/idea/code-with-me/")
  private val DEFAULT_JRE_DOWNLOAD_LOCATION = URI("https://cache-redirector.jetbrains.com/download.jetbrains.com/idea/jbr/")

  private data class DownloadableFileData(
    val fileName: String,
    val url: String,
    val archivePath: Path,
    val targetPath: Path,
    val includeInManifest: (Path) -> Boolean,
    val downloadFuture: CompletableFuture<Boolean> = CompletableFuture()
  )

  private val buildNumberRegex = Regex("""[0-9]{3}\.([0-9]+|SNAPSHOT)""")

  fun getCwmGuestCachesDir(): Path {
    return System.getProperty(cwmTestsGuestCachesSystemProperty)?.let { Path.of(it) }
           ?: (getJetBrainsSystemCachesDir() / DEFAULT_GUEST_CACHES_DIR_NAME)
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
      SystemInfo.isMac -> "-no-jdk.sit"
      else -> error("Current platform is not supported")
    }

    val clientDownloadUrl = "${DEFAULT_CWM_GUEST_DOWNLOAD_LOCATION}CodeWithMeGuest-$hostBuildNumber$platformSuffix"

    val platformString = if (SystemInfo.isMac) "osx-x64" else if (SystemInfo.isWindows) "windows-x64" else "linux-x64"

    val jreBuildParts = jreBuild.split("b")
    require(jreBuildParts.size == 2) { "jreBuild format should be like 12_3_45b6789.0" }
    require(jreBuildParts[0].matches(Regex("^[0-9_]+$"))) { "jreBuild format should be like 12_3_45b6789.0" }
    require(jreBuildParts[1].matches(Regex("^[0-9.]+$"))) { "jreBuild format should be like 12_3_45b6789.0" }

    val jdkVersion = jreBuildParts[0]
    val jdkBuild = jreBuildParts[1]
    val jreDownloadUrl = "${DEFAULT_JRE_DOWNLOAD_LOCATION}jbr_jcef-$jdkVersion-$platformString-b${jdkBuild}.tar.gz"

    val clientName = "CodeWithMeGuest-$hostBuildNumber"
    val jreName = jreDownloadUrl.substringAfterLast('/').removeSuffix(".tar.gz")

    return object : CodeWithMeSessionInfoProvider {
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
  }

  private val currentlyDownloading = ConcurrentHashMap<Path, CompletableFuture<Boolean>>()

  @ApiStatus.Experimental
  fun downloadClientAndJdk(clientBuildVersion: String,
                           progressIndicator: ProgressIndicator): Pair<Path, Path>? {
    require(application.isUnitTestMode || !application.isDispatchThread) { "This method should not be called on UI thread" }
    LOG.info("Downloading Thin Client jdk-build.txt")

    val clientJdkDownloadUrl = "${DEFAULT_CWM_GUEST_DOWNLOAD_LOCATION}CodeWithMeGuest-$clientBuildVersion-jdk-build.txt"

    val jdkBuild = HttpRequests.request(clientJdkDownloadUrl).readString(progressIndicator)

    LOG.info("Downloading Thin Client")
    val sessionInfoResponse = createSessionInfo(clientBuildVersion, jdkBuild, true)
    LOG.info("Generated session info: $sessionInfoResponse")
    return downloadClientAndJdk(sessionInfoResponse, progressIndicator)
  }

  /**
   * @param clientBuildVersion format: 213.1337[.23]
   * @param jreBuild format: 11_0_11b1536.1
   * where 11_0_11 is jdk version, b1536.1 is the build version
   * @returns Pair(path/to/thin/client, path/to/jre)
   *
   * Update this method (any jdk-related stuff) together with:
   *  `setupJdk.gradle`
   *  `setupJbre.gradle`
   *  `org/jetbrains/intellij/build/impl/BundledJreManager.groovy`
   */
  fun downloadClientAndJdk(clientBuildVersion: String,
                           jreBuild: String,
                           progressIndicator: ProgressIndicator): Pair<Path, Path>? {
    require(application.isUnitTestMode || !application.isDispatchThread) { "This method should not be called on UI thread" }

    LOG.info("Downloading Thin Client")
    val sessionInfoResponse = createSessionInfo(clientBuildVersion, jreBuild, true)
    LOG.info("Generated session info: $sessionInfoResponse")
    return downloadClientAndJdk(sessionInfoResponse, progressIndicator)
  }

  /**
   * @returns Pair(path/to/thin/client, path/to/jre)
   */
  fun downloadClientAndJdk(sessionInfoResponse: CodeWithMeSessionInfoProvider,
                           progressIndicator: ProgressIndicator): Pair<Path, Path>? {
    require(application.isUnitTestMode || !application.isDispatchThread) { "This method should not be called on UI thread" }

    val tempDir = FileUtil.createTempDirectory("jb-cwm-dl", null).toPath()

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
      url = sessionInfoResponse.compatibleClientUrl,
      archivePath = tempDir.resolve(guestFileName),
      targetPath = getCwmGuestCachesDir() / (guestName + extractDirSuffix),
      includeInManifest = cwmGuestManifestFilter
    )

    val jdkFullName = sessionInfoResponse.compatibleJreName
    val jdkFileName = "$jdkFullName.${archiveExtensionFromUrl(sessionInfoResponse.compatibleJreUrl)}"
    val jdkData = DownloadableFileData(
      fileName = jdkFileName,
      url = sessionInfoResponse.compatibleJreUrl,
      archivePath = tempDir.resolve(jdkFileName),
      targetPath = getCwmGuestCachesDir() / (jdkFullName + extractDirSuffix),
      includeInManifest = cwmJbrManifestFilter
    )

    val dataList = arrayOf(jdkData, guestData)

    val activity: StructuredIdeActivity? =
      if (dataList.isNotEmpty()) RemoteDevStatisticsCollector.onGuestDownloadStarted()
      else null

    // todo: download in parallel?
    for (data in dataList) {
      // download
      val future = data.downloadFuture

      AppExecutorUtil.getAppScheduledExecutorService().execute {
        try {
          val existingDownloadFuture = synchronized(currentlyDownloading) {
            val existingDownloadInnerFuture = currentlyDownloading[data.targetPath]
            if (existingDownloadInnerFuture != null) {
              existingDownloadInnerFuture
            } else {
              currentlyDownloading[data.targetPath] = data.downloadFuture
              null
            }
          }
          if (existingDownloadFuture != null) {
            LOG.warn("Already downloading and extracting to ${data.targetPath}, will wait until download finished")
            existingDownloadFuture.whenComplete { res, ex -> if (ex != null) { future.completeExceptionally(ex) } else { future.complete(res) } }
            return@execute
          }

          if (isAlreadyDownloaded(data)) {
            LOG.info("Already downloaded and extracted ${data.fileName} to ${data.targetPath}")
            future.complete(true)
            return@execute
          }
          val downloadProgressIndicator = progressIndicator.createSubProgress(0.5 / dataList.count().toDouble())
          downloadProgressIndicator.text = RemoteDevUtilBundle.message("thinClientDownloader.downloading", data.fileName)

          val testInstallersDir = System.getProperty(gatewayTestsInstallersDirProperty)?.let { Path.of(it) }
          val usePreparedArchive = testInstallersDir != null && !data.archivePath.fileName.pathString.contains("jbr")

          try {
            if (usePreparedArchive) {
              fun getPreparedGuestArchive(): Path {
                val archiveName = data.archivePath.name

                // CodeWithMeGuest-213.SNAPSHOT.tar.gz
                val localArchive = testInstallersDir!! / archiveName
                if (localArchive.exists()) return localArchive

                // CodeWithMeGuest-213.2626-no-jbr.tar.gz
                val teamcityArchive = testInstallersDir / archiveName.replace(".tar.gz", "-no-jbr.tar.gz")
                if (teamcityArchive.exists()) return teamcityArchive

                error("No archive with guest was prepared while the $gatewayTestsInstallersDirProperty is set, " +
                      "please build it manually and put at ${localArchive.absolutePathString()} or ${teamcityArchive.absolutePathString()}")
              }

              val preparedGuestArchive = getPreparedGuestArchive()
              LOG.warn("Using prepared archive from ${preparedGuestArchive.absolutePathString()}")

              preparedGuestArchive.copy(data.archivePath)
            }
            else {
              fun download(url: String, path: Path) {
                LOG.info("Downloading $url -> $path")
                HttpRequests.request(url).saveToFile(path, downloadProgressIndicator)
              }

              download(data.url, data.archivePath)

              if (Registry.`is`("codewithme.check.guest.signature")) {
                download(sessionInfoResponse.downloadPgpPublicKeyUrl ?: JetBrainsPgpConstants.JETBRAINS_DOWNLOADS_PGP_SUB_KEYS_URL,
                  tempDir.resolve("KEYS"))
                download(data.url + SHA256_SUFFIX, data.archivePath.addSuffix(SHA256_SUFFIX))
                download(data.url + SHA256_ASC_SUFFIX, data.archivePath.addSuffix(SHA256_ASC_SUFFIX))

                val pgpVerifier = PgpSignaturesVerifier(object : PgpSignaturesVerifierLogger {
                  override fun info(message: String) {
                    LOG.info("Verifying ${data.url} PGP signature: $message")
                  }
                })

                Sha256ChecksumSignatureVerifier(pgpVerifier).verifyChecksumAndSignature(
                  file = data.archivePath,
                  detachedSignatureFile = data.archivePath.addSuffix(SHA256_ASC_SUFFIX),
                  checksumFile = data.archivePath.addSuffix(SHA256_SUFFIX),
                  expectedFileName = data.fileName,
                  untrustedPublicKeyRing = ByteArrayInputStream(Files.readAllBytes(tempDir.resolve("KEYS"))),
                  trustedMasterKey = ByteArrayInputStream(JETBRAINS_DOWNLOADS_PGP_MASTER_PUBLIC_KEY.toByteArray()),
                )
              }
            }
          }
          catch (ex: IOException) {
            LOG.error(ex)
            future.complete(false)
            return@execute
          }

          // extract
          val extractProgressIndicator = progressIndicator.createSubProgress(0.5 / dataList.count().toDouble())
          extractProgressIndicator.text = RemoteDevUtilBundle.message("thinClientDownloader.extracting", data.fileName)

          // downloading a .zip file will get a VirtualFile with a path of `jar://C:/Users/ivan.pashchenko/AppData/Local/Temp/CodeWithMeGuest-212.2033-windows-x64.zip!/`
          // see FileDownloaderImpl.findVirtualFiles making a call to VfsUtil.getUrlForLibraryRoot(ioFile)
          val archivePath = data.archivePath

          LOG.info("Extracting $archivePath to ${data.targetPath}...")
          FileUtil.delete(data.targetPath)

          require(data.targetPath.notExists()) { "Target path \"${data.targetPath}\" for $archivePath already exists" }
          FileManifestUtil.decompressWithManifest(archivePath, data.targetPath, data.includeInManifest)

          require(FileManifestUtil.isUpToDate(data.targetPath,
            data.includeInManifest)) { "Manifest verification failed for archive: $archivePath -> ${data.targetPath}" }
          extractProgressIndicator.fraction = 1.0

          Files.delete(archivePath)
          future.complete(true)
        }
        catch (e: Throwable) {
          LOG.error(e)
          future.complete(false)
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
    catch (e: Throwable) {
      LOG.error(e)
      return null
    }
  }

  private fun isAlreadyDownloaded(fileData: DownloadableFileData): Boolean {
    val extractDirectory = FileManifestUtil.getExtractDirectory(fileData.targetPath, fileData.includeInManifest)
    return extractDirectory.isUpToDate && !fileData.targetPath.fileName.toString().contains("SNAPSHOT")
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

    error("Code With Me Guest home is not found under $guestRoot")
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
        val exeLauncherName = Path.of("bin", "cwm_guest64.exe")
        val exeLauncher = findLauncher(guestRoot, exeLauncherName)
        if (exeLauncher != null) {
          return exeLauncher to listOf(exeLauncher.toString())
        }

        val oldExeLauncherName = Path.of("bin", "intellij_client64.exe")
        val oldExeLauncher = findLauncher(guestRoot, oldExeLauncherName)
        if (oldExeLauncher != null) {
          return oldExeLauncher to listOf(oldExeLauncher.toString())
        }

        val batLauncherName = Path.of("bin", "intellij_client.bat")
        val batLauncher = findLauncher(guestRoot, batLauncherName)
        if (batLauncher != null) {
          return batLauncher to listOf(batLauncher.toString())
        }

        error("Both '$exeLauncherName' and '$batLauncherName' are missing under $guestRoot")
      }

      SystemInfo.isUnix -> {
        if (SystemInfo.isMac) {
          val app = guestRoot.toFile().listFiles { file -> file.name.endsWith(".app") && file.isDirectory }!!.singleOrNull()
          if (app != null) {
            return app.toPath() to listOf("open", app.toString(), "--args")
          }
        }

        val shLauncherName = Path.of("bin", "cwm_guest.sh")
        val shLauncher = findLauncher(guestRoot, shLauncherName)
        if (shLauncher != null) {
          return shLauncher to listOf(shLauncher.toString())
        }

        val oldShLauncherName = Path.of("bin", "intellij_client.sh")
        val oldShLauncher = findLauncher(guestRoot, oldShLauncherName)
        if (oldShLauncher != null) {
          return oldShLauncher to listOf(oldShLauncher.toString())
        }

        error("Could not find launcher '$shLauncherName' under $guestRoot")
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
                                     jdkRoot: Path,
                                     patchVmOptions: ((String) -> String)? = null): Lifetime {
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

    if (patchVmOptions != null) {
      val vmOptionsFile = executable.resolveSibling("cwm_guest64.vmoptions")
      LOG.info("Patching $vmOptionsFile")

      require(vmOptionsFile.isFile() && vmOptionsFile.exists())

      val originalContent = vmOptionsFile.readText(Charsets.UTF_8)
      LOG.info("Original .vmoptions=\n$originalContent")

      val patchedContent = patchVmOptions(originalContent)
      LOG.info("Patched .vmoptions=$patchedContent")

      vmOptionsFile.writeText(patchedContent)
      LOG.info("Patched $vmOptionsFile successfully")
    }

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
        System.getProperty(gatewayTestsX11DisplayProperty)?.let {
          require(SystemInfo.isLinux) { "X11 display property makes sense only on Linux" }
          LOG.info("Setting env var DISPLAY for Guest process=$it")
          commandLine.environment["DISPLAY"] = it
        }

        LOG.info("Starting Code With Me Guest process (attempts left: $attemptCount): ${commandLine}")

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
      } catch (e: IOException) {
        if (link.exists() && link.toRealPath() == targetRealPath) {
          LOG.warn("Creating symlink/junction to already existing target. '$link' -> '$target'")
        } else {
          throw e
        }
      }
      try {
        val linkRealPath = link.toRealPath()
        if (linkRealPath != targetRealPath) {
          LOG.error("Symlink/junction '$link' should point to '$targetRealPath', but points to '$linkRealPath' instead")
        }
      } catch (e: Throwable) {
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
}