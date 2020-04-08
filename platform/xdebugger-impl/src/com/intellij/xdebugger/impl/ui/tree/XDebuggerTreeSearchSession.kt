// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui.tree

import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.find.SearchReplaceComponent
import com.intellij.find.SearchSession
import com.intellij.find.editorHeaderActions.NextOccurrenceAction
import com.intellij.find.editorHeaderActions.PrevOccurrenceAction
import com.intellij.find.editorHeaderActions.StatusTextAction
import com.intellij.find.editorHeaderActions.ToggleMatchCase
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.ex.DefaultCustomComponentAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.XDebuggerBundle
import java.awt.Component
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JPanel

internal class XDebuggerTreeSearchSession(val debuggerTreePanel: XDebuggerTreePanel, val project: Project)
  : SearchSession,
    FindModel.FindModelObserver,
    SearchReplaceComponent.Listener, DataProvider {

  companion object {
    const val DEFAULT_SEARCH_DEPTH = 4
  }

  private val previousHeaderComponent: Component? = debuggerTreePanel.headerComponent
  private val speedSearch = debuggerTreePanel.tree.mySpeedSearch
  private val depthChoice = createDepthChoice()
  private val searchComponent = createSearchComponent()
  private val findModel = createFindModel()
  private var hasMatches: Boolean = false
  private var closeListener = object : KeyListener {
    override fun keyTyped(e: KeyEvent) = processEvent(e)
    override fun keyPressed(e: KeyEvent) = processEvent(e)
    override fun keyReleased(e: KeyEvent?) {}

    private fun processEvent(e: KeyEvent) {
      if (e.keyCode == KeyEvent.VK_ESCAPE) {
        close()
        e.consume()
      }
    }
  }

  init {
    debuggerTreePanel.headerComponent = searchComponent
    debuggerTreePanel.tree.component.addKeyListener(closeListener)
    speedSearch.searchSessionStarted(this)
    IdeFocusManager.getInstance(project).requestFocus(searchComponent.searchTextComponent, false)
  }

  class XDebuggerTreeFindModel : FindModel() {
    var searchDepth: Int = DEFAULT_SEARCH_DEPTH
  }

  private fun createFindModel(): XDebuggerTreeFindModel {
    val findModel = XDebuggerTreeFindModel()
    findModel.copyFrom(FindManager.getInstance(project).findInFileModel)
    findModel.addObserver(this)
    return findModel
  }

  override fun getFindModel(): XDebuggerTreeFindModel {
    return findModel
  }

  override fun findModelChanged(findModel: FindModel) {
    val stringToFind = findModel.stringToFind

    hasMatches = performSearch()
    if (StringUtil.isNotEmpty(stringToFind) && !hasMatches) {
      searchComponent.setNotFoundBackground()
    }
    else {
      searchComponent.setRegularBackground()
    }
    searchComponent.statusText = ""
    searchComponent.update(stringToFind, "", false, findModel.isMultiline)
  }

  override fun getComponent(): SearchReplaceComponent {
    return searchComponent
  }

  override fun hasMatches(): Boolean {
    return hasMatches
  }

  override fun searchForward() {
    speedSearch.nextOccurence(findModel.stringToFind)
  }

  override fun searchBackward() {
    speedSearch.previousOccurence(findModel.stringToFind)
  }

  private fun performSearch(): Boolean {
    return speedSearch.findOccurence(findModel.stringToFind)
  }

  override fun searchFieldDocumentChanged() {
    val textToFind = searchComponent.searchTextComponent.text
    findModel.stringToFind = textToFind
    findModel.isMultiline = textToFind.contains("\n")
  }

  override fun replaceFieldDocumentChanged() {}

  override fun multilineStateChanged() {}

  override fun close() {
    debuggerTreePanel.headerComponent = previousHeaderComponent
    debuggerTreePanel.searchSessionStopped()
    debuggerTreePanel.tree.component.removeKeyListener(closeListener)
    speedSearch.searchSessionStopped()
    IdeFocusManager.getInstance(project).requestFocus(debuggerTreePanel.tree.component, false)
  }

  override fun getData(dataId: String): Any? {
    return if (SearchSession.KEY.`is`(dataId)) this else null
  }

  private fun createDepthChoice(): JComponent {
    val depthSpinner = JBIntSpinner(DEFAULT_SEARCH_DEPTH, 1, 30, 1)
    depthSpinner.addChangeListener {
      findModel.searchDepth = depthSpinner.number
      findModelChanged(findModel)
    }
    val label = JBLabel(XDebuggerBundle.message("xdebugger.variables.search.depth"))
    val panel = JPanel(HorizontalLayout(JBUIScale.scale(UIUtil.DEFAULT_HGAP)))
    panel.add(label)
    panel.add(depthSpinner)
    return panel
  }

  private fun createSearchComponent(): SearchReplaceComponent {
    val searchReplaceComponent = SearchReplaceComponent
      .buildFor(project, debuggerTreePanel.tree.component)
      .addPrimarySearchActions(PrevOccurrenceAction(),
                               NextOccurrenceAction())
      .addExtraSearchActions(ToggleMatchCase(),
                             DefaultCustomComponentAction(Supplier<JComponent> { depthChoice }),
                             StatusTextAction())
      .withCloseAction { close() }
      .withDataProvider(this)
      .build()

    searchReplaceComponent.addListener(this)
    return searchReplaceComponent
  }

}