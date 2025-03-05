package com.intellij.remoteDev.downloader

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil
import com.intellij.remoteDev.RemoteDevUtilBundle
import com.intellij.remoteDev.downloader.exceptions.CodeWithMeDownloaderExceptionHandler
import com.intellij.remoteDev.util.UrlUtil
import com.intellij.util.application
import com.intellij.util.fragmentParameters
import com.jetbrains.rd.util.lifetime.Lifetime
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.pathString

@ApiStatus.Experimental
object CodeWithMeGuestLauncher {
  private val LOG = logger<CodeWithMeGuestLauncher>()

  private val alreadyDownloading = ConcurrentHashMap.newKeySet<String>()

  fun isUnattendedModeUri(uri: URI) = uri.fragmentParameters["jt"] != null

  fun downloadCompatibleClientAndLaunch(
    lifetime: Lifetime?,
    project: Project?,
    clientBuild: BuildNumber?,
    url: String,
    @NlsContexts.DialogTitle product: String,
    onDone: (Lifetime) -> Unit = {}
  ) {
    if (!application.isDispatchThread) {
      // starting a task from background will call invokeLater, but with wrong modality, so do it ourselves
      application.invokeLater({ downloadCompatibleClientAndLaunch(lifetime, project, clientBuild, url, product, onDone) }, ModalityState.any())
      return
    }

    val uri = UrlUtil.parseOrShowError(url, product) ?: return

    if (runAlreadyDownloadedClient(clientBuild, lifetime, project, url, onDone)) {
      return
    }

    if (!alreadyDownloading.add(url)) {
      LOG.info("Already downloading a client for $url")
      return
    }

    ProgressManager.getInstance().run(DownloadAndLaunchClientTask(project, uri, lifetime, url, product, onDone))
  }

  private class DownloadAndLaunchClientTask(
    private val project: Project?,
    private val uri: URI,
    private val lifetime: Lifetime?,
    private val url: String,
    private val product: @NlsContexts.DialogTitle String,
    private val onDone: (Lifetime) -> Unit
  ) : Backgroundable(project, RemoteDevUtilBundle.message("launcher.title"), true) {

    private var clientLifetime : Lifetime = Lifetime.Terminated

    override fun run(progressIndicator: ProgressIndicator) {
      try {
        val sessionInfo = when (uri.scheme) {
          "tcp", "gwws" -> {
            val clientBuildString = uri.fragmentParameters["cb"] ?: error("there is no client build in url")
            val jreBuild = uri.fragmentParameters["jb"] ?: error("there is no jre build in url")
            val clientBuild = BuildNumber.fromStringOrNull(clientBuildString) ?: error("invalid client build: $clientBuildString")
            val unattendedMode = isUnattendedModeUri(uri)

            CodeWithMeClientDownloader.createSessionInfo(clientBuild, jreBuild, unattendedMode)
          }
          "http", "https" -> {
            progressIndicator.text = RemoteDevUtilBundle.message("launcher.get.client.info")
            ThinClientSessionInfoFetcher.getSessionUrl(uri)
          }
          else -> {
            error("scheme '${uri.scheme} is not supported'")
          }
        }

        val parentLifetime = lifetime ?: project?.createLifetime() ?: Lifetime.Eternal
        val frontendInstallation = CodeWithMeClientDownloader.downloadFrontendAndJdk(sessionInfo, progressIndicator)

        clientLifetime = runDownloadedFrontend(
          lifetime = parentLifetime,
          frontendInstallation = frontendInstallation,
          urlForThinClient = url,
          product = product,
          progressIndicator = progressIndicator
        )
      }
      catch (t: Throwable) {
        LOG.warn(t)
        CodeWithMeDownloaderExceptionHandler.handle(product, t)
      }
      finally {
        alreadyDownloading.remove(url)
      }
    }

    override fun onSuccess() = onDone.invoke(clientLifetime)
    override fun onCancel() = Unit
  }

  private fun runAlreadyDownloadedClient(
    clientBuild: BuildNumber?,
    aLifetime: Lifetime?,
    project: Project?,
    url: String,
    onDone: (Lifetime) -> Unit
  ): Boolean {
    if (clientBuild == null) {
      return false
    }
    
    val embeddedClientLauncher = CodeWithMeClientDownloader.createEmbeddedClientLauncherIfAvailable(clientBuild)
    if (embeddedClientLauncher != null) {
      val lifetime = aLifetime ?: project?.createLifetime() ?: Lifetime.Eternal
      val clientLifetime = embeddedClientLauncher.launch(url, lifetime, NotificationBasedEmbeddedClientErrorReporter(project))
      onDone(clientLifetime)
      return true
    }

    val frontendInstallation = findFrontendInstallation(clientBuild)
    if (frontendInstallation == null) return false
    
    val lifetime = aLifetime ?: project?.createLifetime() ?: Lifetime.Eternal
    val clientLifetime = CodeWithMeClientDownloader.runFrontendProcess(
      lifetime = lifetime,
      url = url,
      frontendInstallation = frontendInstallation
    )
    onDone(clientLifetime)
    return true
  }

  private fun findFrontendInstallation(clientBuild: BuildNumber): FrontendInstallation? {
    val customSnapshotInstallation = CodeWithMeClientDownloader.createCustomFrontendSnapshotInstallation(clientBuild)
    if (customSnapshotInstallation != null) return customSnapshotInstallation 

    if (!CodeWithMeClientDownloader.isClientDownloaded(clientBuild)) {
      return null
    }
    val sessionInfo = CodeWithMeClientDownloader.createSessionInfo(clientBuild, null, true)
    val tempDir = FileUtil.createTempDirectory("jb-cwm-dl", null).toPath()
    val clientUrl = URI(sessionInfo.compatibleClientUrl)
    val guestData = CodeWithMeClientDownloader.DownloadableFileData.build(
      url = clientUrl,
      tempDir = tempDir,
      cachesDir = service<JetBrainsClientDownloaderConfigurationProvider>().clientCachesDir,
      includeInManifest = CodeWithMeClientDownloader.getJetBrainsClientManifestFilter(sessionInfo.clientBuildNumber),
    )
    return StandaloneFrontendInstallation(guestData.targetPath, clientBuild, null)
  }

  fun runDownloadedFrontend(lifetime: Lifetime, frontendInstallation: FrontendInstallation, urlForThinClient: String,
                            @NlsContexts.DialogTitle product: String, progressIndicator: ProgressIndicator?): Lifetime {
    // todo: offer to connect as-is?
    try {
      progressIndicator?.text = RemoteDevUtilBundle.message("launcher.launch.client")
      progressIndicator?.text2 = frontendInstallation.installationHome.pathString
      val thinClientLifetime = CodeWithMeClientDownloader.runFrontendProcess(lifetime, urlForThinClient, frontendInstallation)

      // Wait a bit until process will be launched and only after that finish task
      Thread.sleep(3000)

      return thinClientLifetime
    }
    catch (t: Throwable) {
      Logger.getInstance(javaClass).warn(t)
      application.invokeLater {
        Messages.showErrorDialog(
          RemoteDevUtilBundle.message("error.guest.run.issue", t.message ?: "Unknown"),
          product)
      }
      return Lifetime.Terminated
    }
  }
}