// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jsonpath.ui

import com.intellij.json.JsonLanguage
import com.intellij.json.json5.Json5Language
import com.intellij.json.psi.JsonFile
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiAnchor
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.update.DebouncedUpdates
import com.intellij.util.ui.update.UpdateQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration.Companion.milliseconds

internal class JsonPathEvaluateFileView(project: Project, jsonFile: JsonFile, scope: CoroutineScope) : JsonPathEvaluateView(project) {
  private val expressionHighlightingQueue: UpdateQueue<Unit> = DebouncedUpdates.forScope<Unit>(scope, "JSONPATH_EVALUATE", 1000.milliseconds)
    .withContext(Dispatchers.EDT)
    .runLatest { detectChangesInJson() }
    .cancelOnDispose(this)
  private val fileAnchor: PsiAnchor = PsiAnchor.create(jsonFile)
  private val jsonChangeTrackers: List<ModificationTracker> = listOf(JsonLanguage.INSTANCE, Json5Language.INSTANCE).map {
    PsiModificationTracker.getInstance(project).forLanguage(it)
  }
  @Volatile
  private var previousModificationCount: Long = 0

  init {
    val content = BorderLayoutPanel()

    content.addToTop(searchWrapper)
    content.addToCenter(resultWrapper)

    setContent(content)

    initToolbar()

    val messageBusConnection = this.project.messageBus.connect(this)
    messageBusConnection.subscribe(PsiModificationTracker.TOPIC, PsiModificationTracker.Listener {
      expressionHighlightingQueue.queue(Unit)
    })
  }

  private fun detectChangesInJson() {
    val count = previousModificationCount
    val newCount = jsonChangeTrackers.sumOf { it.modificationCount }
    if (newCount != count) {
      previousModificationCount = newCount
      // some JSON documents have been changed
      resetExpressionHighlighting()
    }
  }

  public override fun getJsonFile(): JsonFile? {
    return fileAnchor.retrieve() as? JsonFile
  }
}