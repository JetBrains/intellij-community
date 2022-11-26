// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.CommandLineProcessor
import com.intellij.ide.CommandLineProcessor.scheduleProcessProtocolCommand
import com.intellij.ide.DataManager
import com.intellij.ide.actions.AboutAction
import com.intellij.ide.actions.ActionsCollector
import com.intellij.ide.actions.ShowSettingsAction
import com.intellij.ide.impl.ProjectUtil
import com.intellij.idea.IdeStarter.Companion.openFilesOnLoading
import com.intellij.idea.IdeStarter.Companion.openUriOnLoading
import com.intellij.jna.JnaLoader
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.mac.foundation.Foundation
import com.intellij.ui.mac.foundation.ID
import com.sun.jna.Callback
import io.netty.handler.codec.http.QueryStringDecoder
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.awt.desktop.*
import java.awt.event.MouseEvent
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

internal object MacOSApplicationProvider {
  fun initApplication(log: Logger) {
    try {
      Worker.initMacApplication()
    }
    catch (t: Throwable) {
      log.warn(t)
    }
  }

  private object Worker {
    private val LOG = logger<MacOSApplicationProvider>()

    private val ENABLED = AtomicBoolean(true)

    @Suppress("unused")
    private var UPDATE_CALLBACK_REF: Any? = null

    fun initMacApplication() {
      val desktop = Desktop.getDesktop()
      desktop.setAboutHandler {
        if (LoadingState.COMPONENTS_LOADED.isOccurred) {
          val project = getProject(false)
          AboutAction.perform(project)
          ActionsCollector.getInstance().record(project, ActionManager.getInstance().getAction("About"), null, null)
        }
      }
      desktop.setPreferencesHandler {
        if (LoadingState.COMPONENTS_LOADED.isOccurred) {
          val project = getProject(true)!!
          submit("Settings") {
            ShowSettingsAction.perform(project)
            ActionsCollector.getInstance().record(project, ActionManager.getInstance().getAction("ShowSettings"), null, null)
          }
        }
      }
      desktop.setQuitHandler { _: QuitEvent?, response: QuitResponse ->
        if (LoadingState.COMPONENTS_LOADED.isOccurred) {
          submit("Quit") { ApplicationManager.getApplication().exit() }
          response.cancelQuit()
        }
        else {
          response.performQuit()
        }
      }
      desktop.setOpenFileHandler { event: OpenFilesEvent ->
        val files = event.files
        if (files.isEmpty()) {
          return@setOpenFileHandler
        }

        val list = files.map(File::toPath)
        if (LoadingState.COMPONENTS_LOADED.isOccurred) {
          val project = getProject(false)
          (project?.coroutineScope ?: ApplicationManager.getApplication().coroutineScope).launch {
            ProjectUtil.openOrImportFilesAsync(list = list, location = "MacMenu", projectToClose = project)
          }
        }
        else {
          openFilesOnLoading(list)
        }
      }
      if (JnaLoader.isLoaded()) {
        installAutoUpdateMenu()
        installProtocolHandler()
      }
    }

    private fun installAutoUpdateMenu() {
      val pool = Foundation.invoke("NSAutoreleasePool", "new")
      val app = Foundation.invoke("NSApplication", "sharedApplication")
      val menu = Foundation.invoke(app, Foundation.createSelector("menu"))
      val item = Foundation.invoke(menu, Foundation.createSelector("itemAtIndex:"), 0)
      val appMenu = Foundation.invoke(item, Foundation.createSelector("submenu"))
      val checkForUpdatesClass = Foundation.allocateObjcClassPair(Foundation.getObjcClass("NSMenuItem"), "NSCheckForUpdates")
      val impl = object : Callback {
        @Suppress("unused", "UNUSED_PARAMETER")
        fun callback(self: ID?, selector: String?) {
          SwingUtilities.invokeLater {
            val mouseEvent = MouseEvent(JOptionPane.getRootFrame(), MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, 0, 0, 1, false)
            val actionManager = ActionManager.getInstance()
            actionManager.tryToExecute(actionManager.getAction("CheckForUpdate"), mouseEvent, null, null, false)
          }
        }
      }
      // prevents the callback from being collected
      UPDATE_CALLBACK_REF = impl
      Foundation.addMethod(checkForUpdatesClass, Foundation.createSelector("checkForUpdates"), impl, "v")
      Foundation.registerObjcClassPair(checkForUpdatesClass)
      val checkForUpdates = Foundation.invoke("NSCheckForUpdates", "alloc")
      Foundation.invoke(checkForUpdates, Foundation.createSelector("initWithTitle:action:keyEquivalent:"),
                        Foundation.nsString("Check for Updates..."), Foundation.createSelector("checkForUpdates"), Foundation.nsString(""))
      Foundation.invoke(checkForUpdates, Foundation.createSelector("setTarget:"), checkForUpdates)
      Foundation.invoke(appMenu, Foundation.createSelector("insertItem:atIndex:"), checkForUpdates, 1)
      Foundation.invoke(checkForUpdates, Foundation.createSelector("release"))
      Foundation.invoke(pool, Foundation.createSelector("release"))
    }

    private fun getProject(useDefault: Boolean): Project? {
      @Suppress("DEPRECATION")
      var project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().dataContext)
      if (project == null) {
        LOG.debug("MacMenu: no project in data context")
        val projects = ProjectManager.getInstance().openProjects
        project = projects.firstOrNull()
        if (project == null && useDefault) {
          LOG.debug("MacMenu: use default project instead")
          project = ProjectManager.getInstance().defaultProject
        }
      }
      LOG.debug("MacMenu: project = ", project)
      return project
    }

    private fun submit(name: String, task: Runnable) {
      LOG.debug("MacMenu: on EDT = ", SwingUtilities.isEventDispatchThread(), "; ENABLED = ", ENABLED.get())
      if (!ENABLED.get()) {
        LOG.debug("MacMenu: disabled")
        return
      }

      val component = IdeFocusManager.getGlobalInstance().focusOwner
      if (component != null && IdeKeyEventDispatcher.isModalContext(component)) {
        LOG.debug("MacMenu: component in modal context")
      }
      else {
        ENABLED.set(false)
        ApplicationManager.getApplication().invokeLater({
                                                          try {
                                                            LOG.debug("MacMenu: init ", name)
                                                            task.run()
                                                          }
                                                          finally {
                                                            LOG.debug("MacMenu: done ", name)
                                                            ENABLED.set(true)
                                                          }
                                                        }, ModalityState.NON_MODAL)
      }
    }

    private fun installProtocolHandler() {
      val mainBundle = Foundation.invoke("NSBundle", "mainBundle")
      val urlTypes = Foundation.invoke(mainBundle, "objectForInfoDictionaryKey:", Foundation.nsString("CFBundleURLTypes"))
      if (urlTypes == ID.NIL) {
        val build = ApplicationInfoImpl.getShadowInstance().build
        if (!build.isSnapshot) {
          LOG.warn("""
  No URL bundle (CFBundleURLTypes) is defined in the main bundle.
  To be able to open external links, specify protocols in the app layout section of the build file.
  Example: args.urlSchemes = ["your-protocol"] will handle following links: your-protocol://open?file=file&line=line
  """.trimIndent())
        }
        return
      }
      Desktop.getDesktop().setOpenURIHandler { event ->
        val uri = event.uri
        var uriString = uri.toString()
        if ("open" == uri.host && QueryStringDecoder(uri).parameters()["file"] != null) {
          uriString = CommandLineProcessor.SCHEME_INTERNAL + uriString
        }
        if (LoadingState.APP_STARTED.isOccurred) {
          scheduleProcessProtocolCommand(uriString)
        }
        else {
          openUriOnLoading(uriString)
        }
      }
    }
  }
}