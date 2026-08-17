// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.scopes

import com.intellij.find.FindBundle
import com.intellij.find.impl.FindAndReplaceExecutor
import com.intellij.ide.DataManager
import com.intellij.ide.rpc.rpcId
import com.intellij.ide.util.scopeChooser.FrontendScopeChooser
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.ide.util.scopeChooser.ScopeSeparator
import com.intellij.ide.util.scopeChooser.ScopesFilterConditionType
import com.intellij.ide.util.scopeChooser.ScopesStateService
import com.intellij.ide.util.scopeChooser.createScopeDescriptorRenderer
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.observable.util.whenItemSelected
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.FixedSizeButton
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.cancelOnDispose
import fleet.rpc.client.RpcTimeoutException
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.await
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.UUID
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.min

private val LOG = logger<FrontendScopeChooserImpl>()

/**
 * Instances of `ScopeChooserCombo` **must be disposed** when the corresponding dialog or settings page is closed. Otherwise,
 * listeners registered in `init()` cause memory leak.<br></br><br></br>
 * Example: if `ScopeChooserCombo` is used in a
 * `DialogWrapper` subclass, call `Disposer.register(getDisposable(), myScopeChooserCombo)`, where
 * `getDisposable()` is `DialogWrapper`'s method.
 */
@ApiStatus.Internal
class FrontendScopeChooserImpl(
  private val project: Project,
  private val preselectedScopeName: String?,
  private val filterConditionType: ScopesFilterConditionType = ScopesFilterConditionType.OTHER,
) : JPanel(BorderLayout()), Disposable, FrontendScopeChooser {
  private val modelId = UUID.randomUUID().toString()
  private var scopesMap: Map<String, ScopeDescriptor> = emptyMap()
  private val scopeToSeparator: MutableMap<ScopeDescriptor, ListSeparator> = mutableMapOf()

  private val _comboBox = ComboBox<ScopeDescriptor>(300)
  private var initialSelection: ScopeDescriptor? = null
  private var selectedItem: ScopeDescriptor?
    get() = _comboBox.selectedItem as? ScopeDescriptor
    set(value) {
      if (selectedItem == value) return
      _comboBox.setSelectedItem(value)
    }

  private val editScopesButton = FixedSizeButton(_comboBox).apply {
    addActionListener { editScopes() }
    accessibleContext.accessibleName = FindBundle.message("find.usages.edit.scopes.button.accessible.name")
  }

  private val coroutineScope: CoroutineScope = project.service<FrontendScopeChooserScopeHolder>().coroutineScope
    .childScope("FrontendScopeChooserImpl")

  init {
    _comboBox.renderer =
      createScopeDescriptorRenderer({ descriptor -> scopeToSeparator[descriptor] }, FindBundle.message("find.usages.loading.search.scopes"))
    _comboBox.setSwingPopup(false)
    _comboBox.whenItemSelected {
      val scopeId = selectedScopeId ?: return@whenItemSelected
      if (it.needsUserInputForScope()) {
        FindAndReplaceExecutor.getInstance(project).performScopeSelection(scopeId, project)
      }
    }
    _comboBox.accessibleContext.accessibleName = FindBundle.message("find.usages.scope.combobox.accessible.name")

    val cachedScopes = ScopesStateService.getInstance(project).getCachedScopeDescriptors()
    initItems(cachedScopes)
    initialSelection = selectedItem
    loadItemsAsync()

    add(_comboBox, BorderLayout.CENTER)
    add(editScopesButton, BorderLayout.EAST)
  }

  private fun loadItemsAsync() {
    // loadItemsAsync will be executed on asynchronously on background,
    // it's important to collect data context now,
    // because it's going to change with opening Find in Files dialog
    val dataContextPromise = DataManager.getInstance().dataContextFromFocusAsync.then { Utils.createAsyncDataContext(it) }

    coroutineScope.childScope("ScopesStateService.subscribeToScopeStates").launch {
      val dataContext = dataContextPromise.await()
      durable {
        val scopesFlow = ScopeModelApi.getInstance().createModelAndSubscribe(
          project.projectId(), modelId, filterConditionType, dataContext.rpcId())
        if (scopesFlow == null) {
          LOG.error("Failed to subscribe to model updates for modelId: $modelId")
          scopesMap = emptyMap()
          withContext(Dispatchers.EDT) {
            initItems(emptyList(), null)
          }
          return@durable
        }
        scopesFlow.collect { scopesInfo ->
          val fetchedScopes = scopesInfo.getScopeDescriptors()

          scopesMap = fetchedScopes
          val items = fetchedScopes.values.toList()
          withContext(Dispatchers.EDT) {
            initItems(items, scopesInfo.selectedScopeId)
          }

          ScopesStateService.getInstance(project).getScopesState().updateIfNotExists(fetchedScopes)
        }
      }
    }
  }

  private fun editScopes() {
    val currentSelectionId = selectedScopeId

    val projectId = project.projectId()
    coroutineScope.launch {
      try {
        val deferred = ScopeModelApi.getInstance().openEditScopesDialog(projectId, currentSelectionId, modelId)
        deferred.cancelOnDispose(project)

        val result = deferred.await()
        ApplicationManager.getApplication().invokeLater {
          selectedItem = scopesMap[result]
        }
      }
      catch (e: RpcTimeoutException) {
        LOG.warn("Failed to edit scopes", e)
      }
    }
  }

  override val comboBox: JComboBox<*> get() = _comboBox

  private fun initItems(items: List<ScopeDescriptor>, selectedScopeId: String? = null) {
    // Avoid using initial selection as a previous selection
    // it blocks setting a correct one after receiving data from backend for the first time
    val previousSelection = selectedScopeId?.let { scopesMap[it] } ?: selectedItem.takeIf { it != initialSelection }
    initialSelection = null
    _comboBox.removeAllItems()
    items.filterOutSeparators().forEach { _comboBox.addItem(it) }
    tryToSelectItem(items, previousSelection)
  }

  private fun Collection<ScopeDescriptor>.filterOutSeparators(): List<ScopeDescriptor> {
    var lastSeparator: ScopeSeparator? = null
    scopeToSeparator.clear()
    return this.filter { item ->
      if (item is ScopeSeparator) {
        lastSeparator = item
      }
      else if (lastSeparator != null) {
        scopeToSeparator[item] = ListSeparator(lastSeparator.text)
        lastSeparator = null
      }
      item !is ScopeSeparator
    }
  }

  private fun tryToSelectItem(items: Collection<ScopeDescriptor>, previousSelection: ScopeDescriptor?) {
    items.find { (previousSelection?.displayName ?: preselectedScopeName) == it.displayName }?.let {
      if (!it.needsUserInputForScope()) selectedItem = it
    }
  }

  override fun setMinimumSize(minimumSize: Dimension?) {
    super.setMinimumSize(minimumSize)
  }

  override fun getPreferredSize(): Dimension {
    if (isPreferredSizeSet) {
      return super.getPreferredSize()
    }
    val preferredSize = super.getPreferredSize()
    return Dimension(min(400, preferredSize.width), preferredSize.height)
  }

  override val component: JComponent
    get() = this
  override val selectedScopeName: String?
    get() = selectedItem?.displayName
  override val selectedScopeId: String?
    get() {
      val scopeDescriptor = selectedItem
      return scopesMap.entries.firstOrNull { it.value == scopeDescriptor }?.key
    }

  override fun dispose() {
    coroutineScope.cancel()
    editScopesButton.actionListeners.forEach { editScopesButton.removeActionListener(it) }
  }
}

@Service(Service.Level.PROJECT)
private class FrontendScopeChooserScopeHolder(val coroutineScope: CoroutineScope)
