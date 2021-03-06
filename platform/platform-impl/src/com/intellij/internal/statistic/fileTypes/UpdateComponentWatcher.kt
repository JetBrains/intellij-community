// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.fileTypes

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PermanentInstallationID
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.ObjectUtils
import com.intellij.util.io.HttpRequests
import org.jdom.JDOMException
import java.io.IOException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.*
import java.util.concurrent.TimeUnit

private val EP_NAME = ExtensionPointName<FileTypeStatisticProvider>("com.intellij.fileTypeStatisticProvider")

@Service
private class UpdateComponentWatcher : Disposable {

  init {
    // rely on fact that service will be initialized only once
    // TODO: Use message bus once it will be provided by platform
    val multicaster = EditorFactory.getInstance().eventMulticaster
    if (multicaster is EditorEventMulticasterEx) {
      multicaster.addFocusChangeListener(object : FocusChangeListener {
        override fun focusGained(editor: Editor) {
          scheduleUpdate(editor)
        }
      }, this)
    }
  }

  fun scheduleUpdate(editor: Editor) {
    val fileType = (editor as EditorEx).virtualFile?.fileType ?: return

    // TODO: `accept` can be slow (CloudFormationFileTypeStatisticProvider), we should not call it synchronously
    val ep = EP_NAME.findFirstSafe { it.accept(editor, fileType) } ?: return

    // TODO: Use PluginAware EP
    val plugin = EP_NAME.computeIfAbsent(ep, UpdateComponentWatcher::class.java) {
      Optional.ofNullable(PluginManagerCore.getPlugin(PluginId.getId(ep.pluginId)))
    }
    val pluginIdString = ep.pluginId
    if (!plugin.isPresent) {
      LOG.error("Unknown plugin id: $pluginIdString is reported by ${ep::class.java}")
      return
    }

    val pluginVersion = plugin.get().version
    if (checkUpdateRequired(pluginIdString, pluginVersion)) {
      ApplicationManager.getApplication().executeOnPooledThread {
        update(pluginIdString, pluginVersion)
      }
    }
  }

  override fun dispose() {
  }
}

private class UpdateComponentEditorListener : EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      service<UpdateComponentWatcher>().scheduleUpdate(event.editor)
    }
  }
}

private val lock = ObjectUtils.sentinel("updating_monitor")
private val LOG = logger<UpdateComponentWatcher>()

private fun update(pluginIdString: String, pluginVersion: String) {
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