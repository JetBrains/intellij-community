/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.reactiveidea

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.RecentProjectsManager
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.idea.IdeaApplication
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diff.ApplicationStarterBase
import com.intellij.openapi.util.Ref
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.WindowManagerEx
import java.awt.Toolkit
import java.awt.event.InvocationEvent
import javax.swing.SwingUtilities

public class ServerAppStarter : ApplicationStarterBase("server", 0) {
  companion object {
    val LOG = Logger.getInstance(ServerAppStarter.javaClass)
  }

  override fun getUsageMessage(): String? = "server"

  override fun processCommand(args: Array<out String>?, currentDirectory: String?) {
    assert(ApplicationManager.getApplication().isServer(), "Please set ${IdeaApplication.IDEA_IS_SERVER} system property to true")

    RecentProjectsManager.getInstance()

    // Event queue should not be changed during initialization of application components.
    // It also cannot be changed before initialization of application components because IdeEventQueue uses other
    // application components. So it is proper to perform replacement only here.
    val app = ApplicationManagerEx.getApplicationEx()
    val windowManager = WindowManager.getInstance() as WindowManagerEx
    IdeEventQueue.getInstance().setWindowManager(windowManager)

    val willOpenProject = Ref(java.lang.Boolean.FALSE)
    val lifecyclePublisher = app.getMessageBus().syncPublisher(AppLifecycleListener.TOPIC)
    lifecyclePublisher.appFrameCreated(args, willOpenProject)

    LOG.info("App initialization took " + (System.nanoTime() - PluginManager.startupStart) / 1000000 + " ms")
    PluginManagerCore.dumpPluginClassStatistics()

    app.invokeLater(object : Runnable {
      override fun run() {
//      var projectFromCommandLine: Project? = null
//      if (myPerformProjectLoad) {
//        projectFromCommandLine = (app as IdeaApplication).loadProjectFromExternalCommandLine()
//      }

        val bus = ApplicationManager.getApplication().getMessageBus()
        bus.syncPublisher(AppLifecycleListener.TOPIC).appStarting(null)

        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(object : Runnable {
          override fun run() {
            PluginManager.reportPluginError()
          }
        })
      }
    }, ModalityState.NON_MODAL)

      val eventQueue = Toolkit.getDefaultToolkit().getSystemEventQueue()
      while (true) {
        val event = eventQueue.getNextEvent()
        if (event is InvocationEvent) {
          IdeEventQueue.getInstance().dispatchEvent(event)
        }
      }
  }
}