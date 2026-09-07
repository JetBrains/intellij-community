// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.target

import com.intellij.ide.actions.GotoActionBase
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.util.PsiElementListCellRenderer.ItemMatchers
import com.intellij.ide.util.gotoByName.ChooseByNameInScopeItemProvider
import com.intellij.ide.util.gotoByName.ChooseByNameModel
import com.intellij.ide.util.gotoByName.ChooseByNameModelEx
import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.ide.util.gotoByName.ChooseByNameViewModel
import com.intellij.ide.util.gotoByName.ChooseByNameWeightedItemProvider
import com.intellij.ide.util.gotoByName.FileTypeRef
import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.searchEverywhere.SeExtendedInfo
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.platform.searchEverywhere.presentations.SeTargetItemPresentationBuilder
import com.intellij.platform.searchEverywhere.providers.SeEverywhereFilterImpl
import com.intellij.platform.searchEverywhere.providers.SeScopeById
import com.intellij.platform.searchEverywhere.providers.SeScopeByIdFiles
import com.intellij.platform.searchEverywhere.utils.SuspendLazyProperty
import com.intellij.platform.searchEverywhere.utils.suspendLazy
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FindSymbolParameters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

// TODO:
//    - provide matchers
//    - semantic provider
//    - Target presentation provider

class SeTargetRawItem(val rawItem: Any, val rawWeight: Int?)

class SeTargetPresentableItem(val rawItem: Any,
                              private val matchers: ItemMatchers?,
                              private val weight: Int,
                              private val presentation: TargetPresentation,
                              val extendedInfo: SeExtendedInfo,
                              val isMultiSelectionSupported: Boolean,
                              val isExactMatch: Boolean): SeItem {
  override fun weight(): Int = weight
  override suspend fun presentation(): SeItemPresentation = SeTargetItemPresentationBuilder()
    .withTargetPresentation(presentation, matchers, extendedInfo, isMultiSelectionSupported)
    .build()
}

@ApiStatus.Internal
class SeTargetItemsProvider(private val project: Project,
                            dataContext: DataContext,
                            private val operationDisposable: Disposable?,
                            private val label: String,
                            private val gotoModelProvider: (Project, ScopeDescriptor?, Set<FileTypeRef>) -> (FilteringGotoByModel<*>)) {

  private val psiContext = GotoActionBase.getPsiContext(dataContext)?.let { context ->
    SmartPointerManager.getInstance(project).createSmartPsiElementPointer(context)
  }

  private val scopeById: SuspendLazyProperty<SeScopeById> = suspendLazy {
    SeScopeByIdFiles(project, psiContext)
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun getItemsFlow(params: SeParams, presentationProvider: suspend (SeTargetRawItem) -> SeTargetPresentableItem): Flow<SeTargetPresentableItem> =
    getItemsFlow(params)
      .buffer(capacity = 64)                            // let the search run ahead
      .flatMapMerge(concurrency = 10) { rawItem ->
        flow {
          emit(presentationProvider(rawItem))
        }
      }

  fun getItemsFlow(params: SeParams): Flow<SeTargetRawItem> = channelFlow {
    // The counters live outside the read action, because `readAction` restarts the block after a write action.
    val attemptCount = AtomicInteger(0)
    val sentCount = AtomicInteger(0)
    val startedAtNano = System.nanoTime()
    val pattern = params.inputQuery.trim()
    if (pattern.isBlank()) return@channelFlow

    val (scopeDescriptor, hiddenTypes) = SeEverywhereFilterImpl.isEverywhere(params.filter)?.let { isEverywhere ->
      scopeById.getValue()[isEverywhere] to null
    } ?: run {
      val targetsFilter = SeTargetsFilter.from(params.filter)

      targetsFilter.selectedScopeId?.let {
        scopeById.getValue()[it]
      } to targetsFilter.hiddenTypes
    }

    val scope = scopeDescriptor?.scope as? GlobalSearchScope ?: GlobalSearchScope.projectScope(project)

    val hiddenTypeRefs = hiddenTypes?.toSet()?.let { hiddenTypes ->
      FileSearchEverywhereContributor.getAllFileTypes().filter { hiddenTypes.contains(it.displayName) }
    }?.toSet() ?: emptySet()

    try {
      readAction {
        val attempt = attemptCount.incrementAndGet()
        if (attempt > 1) {
          LOG.debug {
            "$label: read action restarted, attempt=$attempt, ${sentCount.get()} items are re-sent"
          }
        }

        val model = gotoModelProvider(project, scopeDescriptor, hiddenTypeRefs)
        if (operationDisposable != null && model is Disposable) {
          Disposer.register(operationDisposable, model)
        }

        if (!DumbService.isDumbAware(model) && isDumb(project)) {
          LOG.debug {
            "$label: skipped, the model ${model::class.simpleName} is not dumb aware and the project is dumb"
          }
          return@readAction
        }

        val context = psiContext?.element
        val provider = ChooseByNameModelEx.getItemProvider(model, context)
        val isEverywhere = scope.isSearchInLibraries
        val viewModel = MyViewModel(project, model)

        LOG.debug {
          "$label: search start, attempt=$attempt, pattern='$pattern', " +
          "provider=${provider::class.simpleName}, scope='${scope.displayName}', " +
          "isEverywhere=$isEverywhere, psiContext=${context != null}"
        }

        blockingContextToIndicator {
          val progressIndicator = ProgressManager.getGlobalProgressIndicator()
          val fromModelCount = AtomicInteger(0)

          when (provider) {
            is ChooseByNameInScopeItemProvider -> {
              val parameters = FindSymbolParameters.wrap(pattern, scope)
              provider.filterElementsWithWeights(viewModel, parameters, progressIndicator
              ) { item: FoundItemDescriptor<*> ->
                fromModelCount.incrementAndGet()
                processElement(progressIndicator, model, item.item, item.weight, sentCount)
              }
            }
            is ChooseByNameWeightedItemProvider -> {
              provider.filterElementsWithWeights(viewModel, pattern, isEverywhere, progressIndicator
              ) { item: FoundItemDescriptor<*> ->
                fromModelCount.incrementAndGet()
                processElement(progressIndicator, model, item.item, item.weight, sentCount)
              }
            }
            else -> {
              provider.filterElements(viewModel, pattern, isEverywhere, progressIndicator) { element: Any ->
                fromModelCount.incrementAndGet()
                processElement(progressIndicator, model, element, null, sentCount)
              }
            }
          }

          LOG.debug {
            "$label: attempt=$attempt done, elementsFromModel=${fromModelCount.get()}, sent=${sentCount.get()}"
          }
        }
      }
      logSearchOutcome("finished", pattern, sentCount, attemptCount, startedAtNano)
    }
    catch (e: Throwable) {
      // `ProcessCanceledException` is a `CancellationException`, so one check covers both.
      val outcome = if (e is CancellationException) "cancelled" else "failed with ${e::class.simpleName}"
      logSearchOutcome(outcome, pattern, sentCount, attemptCount, startedAtNano)
      throw e
    }
  }

  private fun logSearchOutcome(
    outcome: String, pattern: String, sentCount: AtomicInteger, attemptCount: AtomicInteger, startedAtNano: Long,
  ) {
    LOG.debug {
      "$label: $outcome, pattern='$pattern', sent=${sentCount.get()}, attempts=${attemptCount.get()}, " +
      "durationMs=${(System.nanoTime() - startedAtNano) / 1_000_000}"
    }
  }

  private fun ProducerScope<SeTargetRawItem>.processElement(
    indicator: ProgressIndicator, model: ChooseByNameModel, element: Any?, weight: Int?, sentCount: AtomicInteger,
  ): Boolean {
    if (indicator.isCanceled) {
      LOG.debug {
        "$label: stopped, the indicator is cancelled after ${sentCount.get()} items"
      }
      return false
    }
    if (element == null) {
      LOG.error("SeTargetItemsProvider($label): null returned from $model")
      return true
    }

    runBlockingCancellable {
      LOG.debug {
        "$label: emitting ${element.toString().split('\n').firstOrNull()}, weight=$weight"
      }
      send(SeTargetRawItem(element, weight))
    }
    sentCount.incrementAndGet()

    return true
  }

  companion object {
    private val LOG = logger<SeTargetItemsProvider>()
  }
}

private class MyViewModel(private val myProject: Project, private val myModel: ChooseByNameModel) : ChooseByNameViewModel {
  override fun getProject(): Project = myProject

  override fun getModel(): ChooseByNameModel = myModel

  override fun isSearchInAnyPlace(): Boolean = myModel.useMiddleMatching()

  override fun transformPattern(pattern: String): String = ChooseByNamePopup.getTransformedPattern(pattern, myModel)

  override fun canShowListForEmptyPattern(): Boolean = false

  override fun getMaximumListSizeLimit(): Int = 0
}
