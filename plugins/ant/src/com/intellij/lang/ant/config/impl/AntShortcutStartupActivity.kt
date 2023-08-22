// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.impl

import com.intellij.lang.ant.AntDisposable
import com.intellij.lang.ant.config.AntConfiguration
import com.intellij.lang.ant.config.actions.TargetActionStub
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer

private class AntShortcutStartupActivity : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    val listenerDisposable = Disposer.newDisposable()
    Disposer.register(AntDisposable.getInstance(project), listenerDisposable)
    val actionManager: ActionManager = ApplicationManager.getApplication().serviceAsync<ActionManager>()
    registerActionForKeymap(project, actionManager, KeymapManagerEx.getInstanceEx().activeKeymap)
    ApplicationManager.getApplication().getMessageBus().connect(listenerDisposable)
      .subscribe<KeymapManagerListener>(KeymapManagerListener.TOPIC,
                                        object : KeymapManagerListener {
                                          override fun activeKeymapChanged(keymap: Keymap?) {
                                            if (keymap == null)
                                              return
                                            registerActionForKeymap(project, actionManager, keymap)
                                          }
                                        })

    Disposer.register(AntDisposable.getInstance(project), Disposable {
      unregisterAction(project)
    })
  }
}

private fun registerActionForKeymap(project: Project, actionManager: ActionManager, keymap: Keymap) {
  val prefix: String = AntConfiguration.getActionIdPrefix(project)
  for (id in keymap.actionIdList) {
    if (id.startsWith(prefix) && actionManager.getAction(id) == null) {
      actionManager.registerAction(id, TargetActionStub(id, project))
    }
  }
}

private fun unregisterAction(project: Project) {
  val actionManager = ActionManagerEx.getInstanceEx()
  for (oldId in actionManager.getActionIdList(AntConfiguration.getActionIdPrefix(project))) {
    actionManager.unregisterAction(oldId)
  }
}
