package com.intellij.platform.whatsNew

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.SystemProperties
import com.intellij.util.application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class WhatsNewShowOnStartCheckService : ProjectActivity {
  companion object {
    private val LOG = logger<WhatsNewShowOnStartCheckService>()
  }

  private val ourStarted = AtomicBoolean(false)
  private val isPlaybackMode = SystemProperties.getBooleanProperty("idea.is.playback", false)

  private suspend fun checkConnectionAvailable(): Boolean {
    return withContext(Dispatchers.IO) {
      return@withContext try {
        val url = WhatsNewContentVersionChecker.getUrl()?.let { URL(it) } ?: return@withContext false
        val connection = url.openConnection() as HttpURLConnection

        connection.setConnectTimeout(5000)
        connection.instanceFollowRedirects = false

        connection.connect()
        if (connection.responseCode != 200) {
          LOG.warn("WhatsNew page '$url' not available response code: ${connection.responseCode}")
          false
        }
        else {
          true
        }
      }
      catch (e: Exception) {
        LOG.warn("WhatsNew page connection error: '$e")
        false
      }
    }
  }

  override suspend fun execute(project: Project) {
    if (ourStarted.getAndSet(true)) return
    if (application.isHeadlessEnvironment || application.isUnitTestMode || isPlaybackMode) return

    withContext(Dispatchers.EDT) {
      if (!Registry.`is`("whats.new.enabled")) {
        if(LOG.isTraceEnabled){
          LOG.trace("EapWhatsNew: DISABLED")
        }

        return@withContext
      }

      val isTestMode = Registry.`is`("whats.new.test.mode")

      if(isTestMode) {
        if(LOG.isTraceEnabled){
          LOG.trace("WhatsNew: TEST MODE STARTED")
        }
        replaceAction()
        openWhatsNew(project)
        return@withContext
      }

      val productVersion = WhatsNewContentVersionChecker.productVersion() ?: run {
        if(LOG.isTraceEnabled) {
          LOG.trace("WhatsNew: unknown current version")
        }
        return@withContext
      }

      val linkVersion = WhatsNewContentVersionChecker.linkVersion() ?: run {
        if(LOG.isTraceEnabled) {
          LOG.trace("WhatsNew: unknown link version")
        }
        unregisterAction()
        return@withContext
      }

      if(productVersion.year == linkVersion.year && productVersion.release == linkVersion.release) {
        LOG.trace("WhatsNew: productVersion '$productVersion' linkVersion: '$linkVersion' ")
        replaceAction()

        WhatsNewContentVersionChecker.lastShownLinkVersion()?.let {
          LOG.trace("WhatsNew: link version last: '$it' new: '$linkVersion' ")
          if(it.eap < linkVersion.eap) {
            openWhatsNew(project)
            return@withContext
          }
        } ?: run {
          LOG.trace("WhatsNew: link not saved")
          openWhatsNew(project)
        }
      } else {
        LOG.trace("WhatsNew: link '${WhatsNewContentVersionChecker.getUrl()}' incompatible with this product version: $productVersion ")
        unregisterAction()
      }
    }
  }

  private suspend fun openWhatsNew(project: Project) {
    if(checkConnectionAvailable()) {
      WhatsNewAction.openWhatsNew(project)
    }
  }

  private fun replaceAction() {
    val actionManager = ActionManager.getInstance()
    actionManager.replaceAction("WhatsNewAction", WhatsNewAction())
    if(LOG.isTraceEnabled){
      LOG.trace("WhatsNew: WhatsNewAction replaced by WhatsNewAction")
    }
  }

  private fun unregisterAction() {
    val actionManager = ActionManager.getInstance()
    actionManager.unregisterAction("WhatsNewAction")
    if(LOG.isTraceEnabled){
      LOG.trace("EapWhatsNew: WhatsNewAction unregister")
    }
  }
}