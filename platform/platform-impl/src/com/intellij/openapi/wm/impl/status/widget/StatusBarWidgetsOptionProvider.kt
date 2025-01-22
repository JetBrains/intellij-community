// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status.widget

import com.intellij.ide.IdeBundle
import com.intellij.ide.SearchTopHitProvider
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.codeStyle.WordPrefixMatcher
import java.util.function.Consumer

internal class StatusBarWidgetsOptionProvider : SearchTopHitProvider {
  override fun consumeTopHits(pattern: String, collector: Consumer<Any>, project: Project?) {
    if (project == null) {
      return
    }

    val manager = project.getService<StatusBarWidgetsManager>(StatusBarWidgetsManager::class.java)
    val statusBar = WindowManager.getInstance().getStatusBar(project)
    if (statusBar == null) {
      return
    }

    val matcher = WordPrefixMatcher(pattern)
    for (factory in manager.getWidgetFactories()) {
      if (!manager.canBeEnabledOnStatusBar(factory, statusBar)) {
        continue
      }

      val name = IdeBundle.message("label.show.status.bar.widget", factory.getDisplayName())
      if (matcher.matches(name)) {
        collector.accept(service<StatusBarActionManager>().getActionFor(factory))
      }
    }
  }
}
