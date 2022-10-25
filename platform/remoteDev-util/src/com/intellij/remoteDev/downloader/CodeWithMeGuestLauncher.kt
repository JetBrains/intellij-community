package com.intellij.remoteDev.downloader

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.remoteDev.RemoteDevUtilBundle
import com.intellij.remoteDev.util.UrlUtil
import com.intellij.util.application
import com.intellij.util.fragmentParameters
import com.jetbrains.rd.util.lifetime.Lifetime
import org.jetbrains.annotations.ApiStatus
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Experimental
object CodeWithMeGuestLauncher {
  private val LOG = logger<CodeWithMeGuestLauncher>()

  private val alreadyDownloading = ConcurrentHashMap.newKeySet<String>()

  fun isUnattendedModeUri(uri: URI) = uri.fragmentParameters["jt"] != null

  fun downloadCompatibleClientAndLaunch(project: Project?, url: String, @NlsContexts.DialogTitle product: String, onDone: (Lifetime) -> Unit) {
    if (!application.isDispatchThread) {
      // starting a task from background will call invokeLater, but with wrong modality, so do it ourselves
      application.invokeLater({ downloadCompatibleClientAndLaunch(project, url, product, onDone) }, ModalityState.any())
      return
    }

    val uri = UrlUtil.parseOrShowError(url, product) ?: return

    if (!alreadyDownloading.add(url)) {
      LOG.info("Already downloading a client for $url")
      return
    }

    ProgressManager.getInstance().run(object : Backgroundable(project, RemoteDevUtilBundle.message("launcher.title"), true) {

      private var clientLifetime : Lifetime = Lifetime.Terminated

      override fun run(progressIndicator: ProgressIndicator) {
        try {
          val sessionInfo = when (uri.scheme) {
            "tcp", "gwws" -> {
              val clientBuild = uri.fragmentParameters["cb"] ?: error("there is no client build in url")
              val jreBuild = uri.fragmentParameters["jb"] ?: error("there is no jre build in url")
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

          val extractedJetBrainsClientData = CodeWithMeClientDownloader.downloadClientAndJdk(sessionInfo, progressIndicator)
          if (extractedJetBrainsClientData == null) return

          clientLifetime = runDownloadedClient(
            lifetime = project?.createLifetime() ?: Lifetime.Eternal,
            extractedJetBrainsClientData = extractedJetBrainsClientData,
            urlForThinClient = url,
            product = product,
            progressIndicator = progressIndicator
          )
        }
        catch (t: Throwable) {
          LOG.warn(t)
          application.invokeLater({
            Messages.showErrorDialog(
              RemoteDevUtilBundle.message("error.url.issue", t.message ?: "Unknown"),
              product)
          }, ModalityState.any())
        }
        finally {
          alreadyDownloading.remove(url)
        }
      }

      override fun onSuccess() = onDone.invoke(clientLifetime)
      override fun onCancel() = Unit
    })
  }

  fun runDownloadedClient(lifetime: Lifetime, extractedJetBrainsClientData: ExtractedJetBrainsClientData, urlForThinClient: String,
                          @NlsContexts.DialogTitle product: String, progressIndicator: ProgressIndicator?): Lifetime {
    // todo: offer to connect as-is?
    try {
      progressIndicator?.text = RemoteDevUtilBundle.message("launcher.launch.client")
      progressIndicator?.text2 = extractedJetBrainsClientData.clientDir.toString()
      val thinClientLifetime = CodeWithMeClientDownloader.runCwmGuestProcessFromDownload(lifetime, urlForThinClient, extractedJetBrainsClientData)

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