// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.fileTypes

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.ObjectUtils
import com.intellij.util.io.HttpRequests
import org.jdom.JDOMException
import java.io.IOException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

private class UpdateComponentEditorListener : EditorFactoryListener {

  init {
    FileTypeStatisticProvider.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<FileTypeStatisticProvider> {
      override fun extensionRemoved(extension: FileTypeStatisticProvider, pluginDescriptor: PluginDescriptor) {
        Disposer.dispose(extension)
      }
    }, null)
  }

  override fun editorCreated(event: EditorFactoryEvent) {
    if (ApplicationManager.getApplication().isUnitTestMode) return

    val document = event.editor.document
    val file = FileDocumentManager.getInstance().getFile(document) ?: return

    val fileType = file.fileType
    FileTypeStatisticProvider.EP_NAME.extensionList.find { ep -> ep.accept(event, fileType) }?.let { ep ->
      updateOnPooledThread(ep)
      (event.editor as? EditorEx)?.addFocusListener(FocusChangeListener {
        updateOnPooledThread(ep)
      }, ep)
    }
  }

  companion object {
    private val lock = ObjectUtils.sentinel("updating_monitor")
    private val LOG = Logger.getInstance(UpdateComponentEditorListener::class.java)

    private fun updateOnPooledThread(ep: FileTypeStatisticProvider) =
      ApplicationManager.getApplication().executeOnPooledThread {
        update(ep)
      }

    private fun update(ep: FileTypeStatisticProvider) {
      val pluginIdString = ep.pluginId
      val plugin = PluginManagerCore.getPlugin(PluginId.getId(pluginIdString))
      if (plugin == null) {
        LOG.error("Unknown plugin id: $pluginIdString is reported by ${ep::class.java}")
        return
      }

      val pluginVersion = plugin.version

      if (!checkUpdateRequired(pluginIdString, pluginVersion)) return
      val url = getUpdateUrl(pluginIdString, pluginVersion)
      sendRequest(url)
    }

    private fun checkUpdateRequired(pluginIdString: String, pluginVersion: String): Boolean {
      synchronized(lock) {
        val lastVersionKey = "$pluginIdString.LAST_VERSION"
        val lastUpdateKey = "$pluginIdString.LAST_UPDATE"

        val properties = PropertiesComponent.getInstance()
        val lastPluginVersion = properties.getValue(lastVersionKey)

        val lastUpdate = properties.getLong(lastUpdateKey, 0L)
        val shouldUpdate = lastUpdate == 0L
                           || System.currentTimeMillis() - lastUpdate > TimeUnit.DAYS.toMillis(1)
                           || lastPluginVersion == null
                           || lastPluginVersion != pluginVersion
        if (!shouldUpdate) return false

        properties.setValue(lastUpdateKey, System.currentTimeMillis().toString())
        properties.setValue(lastVersionKey, pluginVersion)
        return true
      }
    }

    private fun sendRequest(url: String) {
      try {
        HttpRequests.request(url).connect {
          try {
            JDOMUtil.load(it.reader)
          }
          catch (e: JDOMException) {
            LOG.warn(e)
          }
          LOG.info("updated: $url")
        }
      }
      catch (ignored: UnknownHostException) {
        // No internet connections, no need to log anything
      }
      catch (e: IOException) {
        LOG.warn(e)
      }
    }

    private fun getUpdateUrl(pluginIdString: String, pluginVersion: String): String {
      val applicationInfo = ApplicationInfoEx.getInstanceEx()
      val buildNumber = applicationInfo.build.asString()
      val os = URLEncoder.encode("${SystemInfo.OS_NAME} ${SystemInfo.OS_VERSION}", Charsets.UTF_8.name())
      val uid = PermanentInstallationID.get()
      val baseUrl = "https://plugins.jetbrains.com/plugins/list"
      return "$baseUrl?pluginId=$pluginIdString&build=$buildNumber&pluginVersion=$pluginVersion&os=$os&uuid=$uid"
    }
  }

}