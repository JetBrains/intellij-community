// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.ide.ui.search.ConfigurableHit
import com.intellij.ide.ui.search.SearchableOptionsRegistrar
import com.intellij.ide.ui.search.SearchableOptionsRegistrarImpl
import com.intellij.internal.statistic.collectors.fus.ui.SettingsCounterUsagesCollector
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.LightColors
import com.intellij.ui.SearchTextField
import com.intellij.ui.speedSearch.ElementFilter
import com.intellij.ui.treeStructure.SimpleNode
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.event.DocumentEvent
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.milliseconds

abstract class SettingsFilter @ApiStatus.Internal protected constructor(
  project: Project?,
  groups: List<ConfigurableGroup>,
  search: SearchTextField,
  private val coroutineScope: CoroutineScope,
) : ElementFilter.Active.Impl<SimpleNode?>() {
  @JvmField
  internal val context: OptionsEditorContext = OptionsEditorContext()
  private val project: Project?

  private val search: SearchTextField
  private val groups: List<ConfigurableGroup>

  private var filtered: Set<Configurable>? = null
  private var hits: ConfigurableHit? = null

  private var isUpdateRejected = false
  private var isLastSelected: Configurable? = null

  private val searchableOptionRegistrar: Deferred<SearchableOptionsRegistrar>
  private var job: Job? = null

  init {
    val optionRegistrar = serviceIfCreated<SearchableOptionsRegistrar>() as SearchableOptionsRegistrarImpl?
    if (optionRegistrar == null || !optionRegistrar.isInitialized()) {
      // if not yet computed, preload it to ensure that will be no delay on user typing
      searchableOptionRegistrar = coroutineScope.async {
        val r = serviceAsync<SearchableOptionsRegistrar>() as SearchableOptionsRegistrarImpl
        r.initialize()
        r
      }
    }
    else {
      searchableOptionRegistrar = CompletableDeferred(optionRegistrar)
    }

    this@SettingsFilter.project = project
    this@SettingsFilter.groups = groups
    this@SettingsFilter.search = search
    search.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(event: DocumentEvent) {
        update(type = event.type, adjustSelection = true, now = false)
        // request focus if needed on changing the filter text
        val manager = IdeFocusManager.findInstanceByComponent(this@SettingsFilter.search)
        if (manager.getFocusedDescendantFor(this@SettingsFilter.search) == null) {
          manager.requestFocus(this@SettingsFilter.search, true)
        }
      }
    })
    search.textEditor.addMouseListener(object : MouseAdapter() {
      override fun mousePressed(event: MouseEvent?) {
        if (!search.text.isEmpty()) {
          if (!context.isHoldingFilter) {
            context.isHoldingFilter = true
            updateSpotlight(false)
          }
          if (!search.textEditor.isFocusOwner) {
            search.selectText()
          }
        }
      }
    })
  }

  @ApiStatus.Internal
  protected abstract fun getConfigurable(node: SimpleNode?): Configurable?

  @ApiStatus.Internal
  protected abstract fun findNode(configurable: Configurable?): SimpleNode?

  @ApiStatus.Internal
  protected abstract fun updateSpotlight(now: Boolean)

  override fun shouldBeShowing(node: SimpleNode?): Boolean {
    var node = node
    val filtered = filtered ?: return true
    var configurable = getConfigurable(node)
    if (configurable != null) {
      if (!filtered.contains(configurable)) {
        if (hits != null) {
          val configurables = hits!!.nameFullHits
          while (node != null) {
            if (configurable != null && configurables.contains(configurable)) {
              return true
            }
            node = node.parent
            configurable = getConfigurable(node)
          }
        }
        return false
      }
    }
    return true
  }

  fun setFilterText(text: String) {
    search.text = text
  }

  fun isEmptyFilter(): Boolean = search.text.isNullOrEmpty()

  @ApiStatus.Internal
  fun getFilterText(): String = search.text?.trim() ?: ""

  @ApiStatus.Internal
  fun getSpotlightFilterText(): String = if (hits == null) getFilterText() else hits!!.spotlightFilter

  fun contains(configurable: Configurable): Boolean = hits != null && hits!!.nameHits.contains(configurable)

  fun update(text: String?) {
    try {
      isUpdateRejected = true
      search.text = text
    }
    finally {
      isUpdateRejected = false
    }
    update(type = DocumentEvent.EventType.CHANGE, adjustSelection = false, now = true)
  }

  private fun update(type: DocumentEvent.EventType, adjustSelection: Boolean, now: Boolean) {
    job?.cancel()
    job = coroutineScope.launch {
      delay(100.milliseconds)
      val registrar = searchableOptionRegistrar.await()
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        update(optionRegistrar = registrar, type = type, adjustSelection = adjustSelection, now = now)
      }
    }
  }

  private suspend fun update(
    optionRegistrar: SearchableOptionsRegistrar,
    type: DocumentEvent.EventType,
    adjustSelection: Boolean,
    now: Boolean,
  ) {
    if (isUpdateRejected) {
      return
    }

    val text = getFilterText()
    if (text.isEmpty()) {
      context.isHoldingFilter = false
      hits = null
      filtered = null
    }
    else {
      context.isHoldingFilter = true
      hits = optionRegistrar.getConfigurables(groups, type, null, text, project)
      filtered = hits!!.all
    }

    coroutineContext.ensureActive()

    search.textEditor.setBackground(if (filtered != null && filtered!!.isEmpty()) LightColors.RED else UIUtil.getTextFieldBackground())

    val current = context.currentConfigurable
    var shouldMoveSelection = hits == null || !hits!!.nameFullHits.contains(current) && !hits!!.contentHits.contains(current)

    if (shouldMoveSelection && type != DocumentEvent.EventType.INSERT && (filtered == null || filtered!!.contains(current))) {
      shouldMoveSelection = false
    }

    var candidate = if (adjustSelection) current else null
    if (shouldMoveSelection && hits != null) {
      if (!hits!!.nameHits.isEmpty()) {
        candidate = findConfigurable(hits!!.nameHits, hits!!.nameFullHits)
      }
      else if (!hits!!.contentHits.isEmpty()) {
        candidate = findConfigurable(hits!!.contentHits, null)
      }
    }
    updateSpotlight(now = false)

    if ((filtered == null || !filtered!!.isEmpty()) && candidate == null && isLastSelected != null) {
      candidate = isLastSelected
      isLastSelected = null
    }
    if (candidate == null && current != null) {
      isLastSelected = current
    }

    if (filtered != null && candidate != null) {
      SettingsCounterUsagesCollector.SEARCH.log(getUnnamedConfigurable(candidate).javaClass, filtered!!.size, text.length)
    }

    val node = if (adjustSelection) findNode(candidate) else null
    fireUpdate(node, adjustSelection, now)
  }

  fun reload() {
    isLastSelected = null
    filtered = null
    hits = null
    search.text = ""
    context.reload()
  }
}

private fun getUnnamedConfigurable(candidate: Configurable): UnnamedConfigurable {
  return if (candidate is ConfigurableWrapper) candidate.getConfigurable() else candidate
}

private fun findConfigurable(configurables: Collection<Configurable>, hits: Collection<Configurable>?): Configurable? {
  var candidate: Configurable? = null
  for (configurable in configurables) {
    if (hits != null && hits.contains(configurable)) {
      return configurable
    }
    if (candidate == null && !isEmptyParent(configurable)) {
      candidate = configurable
    }
  }
  return candidate
}

private fun isEmptyParent(configurable: Configurable?): Boolean {
  val parent = ConfigurableWrapper.cast(SearchableConfigurable.Parent::class.java, configurable)
  return parent != null && !parent.hasOwnContent()
}
