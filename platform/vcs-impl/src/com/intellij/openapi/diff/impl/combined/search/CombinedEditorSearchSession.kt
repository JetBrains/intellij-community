// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.combined.search

import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.find.*
import com.intellij.find.editorHeaderActions.*
import com.intellij.find.impl.livePreview.LivePreviewController
import com.intellij.find.impl.livePreview.SearchResults
import com.intellij.find.impl.livePreview.SearchResults.SearchResultsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEventMulticasterEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import org.jetbrains.annotations.NotNull
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import javax.swing.JComponent

internal class CombinedEditorSearchSession(private val project: Project,
                                           private var currentEditor: Editor,
                                           private val closeAction: () -> Unit,
                                           parentComponent: JComponent,
                                           disposableParent: Disposable
) : SearchSession, UiDataProvider {

  private val disposable = Disposer.newCheckedDisposable().also {
    Disposer.register(it, this::close)
    Disposer.register(disposableParent, it)
  }

  private var findModel = EditorSearchSession.createDefaultFindModel(project, currentEditor)
  private val modelObserver = FindModelObserver()
  private val searchComponent: SearchReplaceComponent

  private var currentSessionIndex: Int = 0
  private var currentSessionPosition: Position = Position.FIRST
  private var holders: List<EditorSearchSessionHolder> = emptyList()
  private var currentSession: EditorSearchSession = EditorSearchSessionEx(currentEditor, project, findModel, ::close)

  private val listeners = EventDispatcher.create(CombinedEditorSearchSessionListener::class.java)

  private inner class EditorSearchSessionHolder(private val sessions: List<EditorSearchSessionEx>) {
    constructor(project: Project, editors: List<Editor>) :
      this(editors.map { editor -> EditorSearchSessionEx(editor, project, findModel, this@CombinedEditorSearchSession::close) })

    fun isSearchInProgress() = sessions.any(EditorSearchSession::isSearchInProgress)
    fun hasMatches() = sessions.any(EditorSearchSession::hasMatches)
    fun getMatchesCount() = sessions.sumOf { session -> session.searchResults.matchesCount }
    fun setMatchesLimit(limit: Int) = sessions.forEach { session -> session.searchResults.matchesLimit = limit }
    fun clearResults() = sessions.forEach { session -> session.searchResults.clear() }
    fun addResultListener(listener: SearchResultsListener) = sessions.forEach { session -> session.searchResults.addListener(listener) }
    fun initLivePreview() = sessions.forEach(EditorSearchSession::initLivePreview)
    fun disableLivePreview() = sessions.forEach(EditorSearchSession::disableLivePreview)
    fun disposeLivePreview() = sessions.forEach(EditorSearchSessionEx::disposeLivePreview)
    fun close() = sessions.forEach(EditorSearchSessionEx::closeSession)
    fun getOrNull(position: Position): EditorSearchSession? = sessions.getOrNull(position.index)
    fun indexOf(editor: Editor): Position? = Position.entries.find { it.index == sessions.indexOfFirst { session -> session.editor == editor } }
  }

  private enum class Position(val index: Int) {
    FIRST(0), SECOND(1), THIRD(2)
  }

  init {
    searchComponent = SearchReplaceComponent.buildFor(project, parentComponent, this)
      .addPrimarySearchActions(*createPrimarySearchActions())
      .addExtraSearchActions(
        ToggleMatchCase(),
        ToggleWholeWordsOnlyAction(),
        ToggleRegex())
      .addSearchFieldActions(RestorePreviousSettingsAction())
      .addExtraReplaceAction(TogglePreserveCaseAction())
      .addReplaceFieldActions(PrevOccurrenceAction(false),
                              NextOccurrenceAction(false))
      .withCloseAction(::close)
      .build()

    searchComponent.addListener(MySearchComponentListener())
    registerEditorsFocusListener()

    UiNotifyConnector.installOn(searchComponent, object : Activatable {
      override fun showNotify() {
        holders.forEach { it.initLivePreview() }
      }

      override fun hideNotify() {
        holders.forEach { it.disableLivePreview() }
      }
    })
  }

  private fun createPrimarySearchActions(): Array<AnAction> {
    return EditorSearchSession.createPrimarySearchActions()
      .asSequence()
      .map { action -> if (action is StatusTextAction) WiderStatusTextAction() else action }
      .toList().toTypedArray()
  }

  fun addListener(listener: CombinedEditorSearchSessionListener) {
    listeners.addListener(listener, disposable)
  }

  fun <EditorHolder> update(items: List<EditorHolder>, mapper: (EditorHolder) -> List<Editor>, currentEditor: Editor = this.currentEditor) {
    val emptyUpdate = items.isEmpty()
    val update = holders.isNotEmpty() || !emptyUpdate

    holders.forEach(EditorSearchSessionHolder::disposeLivePreview)

    findModel = FindModel().apply {
      copyFrom(findModel)
      addObserver(modelObserver)
    }

    holders = items.asSequence().map(mapper).filterNot(List<Editor>::isEmpty).map { editors ->
      EditorSearchSessionHolder(project, editors).also { holder -> holder.addResultListener(MySearchResultsListener()) }
    }.toList()

    if (emptyUpdate) {
      component.updateUIWithResults(0, false)
    }
    else {
      updateCurrentState(currentEditor)
    }

    if (update) {
      holders.forEach(EditorSearchSessionHolder::initLivePreview)
      EditorSearchSession.updateEmptyText(component, findModel, null)
    }

    EditorSearchSession.updateUIWithFindModel(component, findModel, if (!emptyUpdate) this.currentEditor else null)
  }

  private inner class FindModelObserver : FindModel.FindModelObserver {
    var updating = false

    override fun findModelChanged(model: FindModel) {
      if (updating) return
      try {
        updating = true
        EditorSearchSession.updateUIWithFindModel(component, model, currentEditor)
        updateResults()
        FindUtil.updateFindInFileModel(project, model, !ConsoleViewUtil.isConsoleViewEditor(currentEditor))
      }
      finally {
        updating = false
      }
    }
  }

  private fun registerEditorsFocusListener() {
    val focusListener = MyEditorFocusListener()
    (EditorFactory.getInstance().eventMulticaster as? EditorEventMulticasterEx)?.addFocusChangeListener(focusListener, disposable)
  }

  private inner class MyEditorFocusListener : FocusChangeListener {

    override fun focusGained(editor: Editor) {
      changeCurrent(editor)
    }

    private fun changeCurrent(newEditor: Editor) {
      if (newEditor != currentEditor) {
        updateCurrentState(newEditor)
      }
    }
  }

  private fun updateCurrentState(newCurrentEditor: Editor) {
    for ((sessionIndex, session) in holders.withIndex()) {
      val newPosition = session.indexOf(newCurrentEditor) ?: continue

      updateCurrentState(sessionIndex, newPosition)
      break
    }
  }

  private fun updateCurrentState(sessionIndex: Int, sessionPosition: Position) {
    val newCurSession = holders[sessionIndex].getOrNull(sessionPosition) ?: return

    currentSessionPosition = sessionPosition
    currentSessionIndex = sessionIndex
    currentSession = newCurSession
    currentEditor = newCurSession.editor
  }

  private fun updateResults() {
    val text = findModel.stringToFind
    if (text.isEmpty()) {
      nothingToSearchFor()
      return
    }

    if (findModel.isRegularExpressions) {
      try {
        Pattern.compile(text)
      }
      catch (e: PatternSyntaxException) {
        component.setNotFoundBackground()
        holders.forEach(EditorSearchSessionHolder::clearResults)
        component.statusText = FindBundle.message(SearchSession.INCORRECT_REGEXP_MESSAGE_KEY)
        return
      }

      if (text.matches("\\|+".toRegex())) {
        nothingToSearchFor()
        component.statusText = ApplicationBundle.message("editorsearch.empty.string.matches")
        return
      }
    }

    val findManager = FindManager.getInstance(project)
      findManager.setFindWasPerformed()
      val copy = FindModel()
      copy.copyFrom(findModel)
      copy.isReplaceState = false
      findManager.findNextModel = copy
  }

  private fun nothingToSearchFor() {
    component.updateUIWithResults(0, true)
    holders.forEach(EditorSearchSessionHolder::clearResults)
  }

  private fun SearchReplaceComponent.updateUIWithResults(matches: Int, regularBackground: Boolean) {
    if (regularBackground) {
      setRegularBackground()
    }
    else {
      setNotFoundBackground()
    }
    listeners.multicaster.statusTextChanged(matches, holders.size)
  }

  fun setStatusText(status: @NotNull @NlsContexts.Label String) {
    component.statusText = status
  }

  override fun getFindModel(): FindModel = findModel

  override fun getComponent(): SearchReplaceComponent = searchComponent

  override fun hasMatches(): Boolean {
    return holders.any(EditorSearchSessionHolder::hasMatches)
  }

  override fun isSearchInProgress(): Boolean {
    return holders.any(EditorSearchSessionHolder::isSearchInProgress)
  }

  override fun searchForward() {
    search(true)
  }

  override fun searchBackward() {
    search(false)
  }

  private fun search(forward: Boolean, curSessionIdx: Int = currentSessionIndex, curSessionPosition: Position = currentSessionPosition) {

    var curSession = holders[curSessionIdx].getOrNull(curSessionPosition)

    if (curSession != null && curSession.canSearch(forward)) {
      curSession.search(forward)
      return
    }

    val furtherIdx = curSessionIdx + (if (forward) 1 else -1)

    if (furtherIdx !in holders.indices) return

    curSession = holders[furtherIdx].getOrNull(curSessionPosition)

    if (curSession != null && curSession.hasMatches()) {
      listeners.multicaster.onSearch(forward, curSession.editor)
      if (forward) curSession.moveCaretToStart() else curSession.moveCaretToEnd() // these ensure the right position of SearchResults.myCursor
      updateCurrentState(furtherIdx, curSessionPosition)
      if (curSession.canSearch(forward)) {
        curSession.search(forward)
      }
    }
    else {
      search(forward, furtherIdx, curSessionPosition)
    }
  }

  private fun EditorSearchSession.search(forward: Boolean) {
    if (forward) searchForward() else searchBackward()
  }

  private fun EditorSearchSession.moveCaretToStart() {
    if (hasMatches()) {
      editor.caretModel.moveToOffset(0)
    }
  }

  private fun EditorSearchSession.moveCaretToEnd() {
    if (hasMatches()) {
      editor.caretModel.moveToOffset(editor.document.textLength)
    }
  }

  private fun EditorSearchSession.hasSingleOccurence(): Boolean {
    return searchResults.occurrences.size == 1
  }

  private fun EditorSearchSession.canSearch(forward: Boolean): Boolean {
    if (!hasMatches()) return false

    if (hasSingleOccurence() && searchResults.occurrenceAtCaret != null) return false

    val firstOccurenceAroundCaret = if (forward) searchResults.firstOccurrenceAfterCaret() else searchResults.firstOccurrenceBeforeCaret()

    return firstOccurenceAroundCaret != null
  }

  override fun close() {
    closeAction()
    holders.forEach(EditorSearchSessionHolder::close)
  }

  private inner class MySearchComponentListener : SearchReplaceComponent.Listener {

    override fun searchFieldDocumentChanged() {
      holders.forEach { it.setMatchesLimit(LivePreviewController.MATCHES_LIMIT) }
      val text = component.searchTextComponent.getText()
      findModel.setStringToFind(text)
      updateResults()
      findModel.isMultiline = component.searchTextComponent.getText().contains("\n") ||
                              component.replaceTextComponent.getText().contains("\n")
    }

    override fun replaceFieldDocumentChanged() {
      holders.forEach { it.setMatchesLimit(LivePreviewController.MATCHES_LIMIT) }
      findModel.setStringToReplace(component.replaceTextComponent.getText())
      findModel.isMultiline = component.searchTextComponent.getText().contains("\n") ||
                              component.replaceTextComponent.getText().contains("\n")
    }

    override fun multilineStateChanged() {
      findModel.isMultiline = component.isMultiline
    }

    override fun toggleSearchReplaceMode() {
      findModel.setReplaceState(!findModel.isReplaceState)
    }
  }

  private inner class MySearchResultsListener : SearchResultsListener {
    override fun searchResultsUpdated(sr: SearchResults) {
      if (sr.findModel == null) return

      if (component.searchTextComponent.getText().isEmpty()) {
        component.updateUIWithResults(0, true)
        return
      }

      val matches = holders.sumOf(EditorSearchSessionHolder::getMatchesCount)

      component.updateUIWithResults(matches, matches > 0)

      component.updateActions()
    }

    override fun cursorMoved() {
      component.updateActions()
    }
  }

  override fun uiDataSnapshot(sink: DataSink) {
    DataSink.uiDataSnapshot(sink, currentSession)
    sink[SearchSession.KEY] = this
  }

  private class WiderStatusTextAction : StatusTextAction() {
    override fun getTextToCountPreferredSize(): String = "9888 results in 100500 files"
  }

  private class EditorSearchSessionEx(editor: Editor, project: Project, findModel: FindModel,
                                      private val combinedSessionClose: () -> Unit) : EditorSearchSession(editor, project, findModel) {
    override fun close() {
      combinedSessionClose()
    }

    fun closeSession() {
      disposeLivePreview()
    }

    public override fun disposeLivePreview() {
      super.disposeLivePreview()
    }
  }
}
