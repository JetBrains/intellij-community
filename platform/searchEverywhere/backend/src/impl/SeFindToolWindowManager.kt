// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter
import com.intellij.find.impl.SearchEverywhereItem
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper.toPsi
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.util.TooManyUsagesStatus
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.backend.providers.target.SeTargetItem
import com.intellij.platform.searchEverywhere.backend.providers.text.SeTextSearchItem
import com.intellij.platform.searchEverywhere.providers.SeAdaptedItem
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.providers.SeProvidersHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageLimitUtil
import com.intellij.usages.UsageTarget
import com.intellij.usages.UsageViewManager
import com.intellij.usages.UsageViewManagerWithUsageViewFactoryCallback
import com.intellij.usages.UsageViewPresentation
import com.intellij.usages.impl.UsageViewManagerImpl
import com.intellij.util.containers.toArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

private val LOG = logger<SeFindToolWindowManager>()

@ApiStatus.Internal
class SeFindToolWindowManager(private val project: Project) {
  suspend fun openInFindToolWindow(
    providerIds: List<SeProviderId>,
    params: SeParams,
    isAllTab: Boolean,
    providersHolder: SeProvidersHolder,
    projectId: ProjectId,
  ) {
    val contributorsString = providerIds.mapNotNull { providerId ->
      providersHolder.get(providerId, isAllTab)?.displayName
    }.joinToString(separator = ", ")
    val presentation = UsageViewPresentation()
    val tabCaptionText = IdeBundle.message("searcheverywhere.found.matches.title", params.inputQuery, contributorsString)
    presentation.codeUsagesString = tabCaptionText
    presentation.targetsNodeText = IdeBundle.message("searcheverywhere.found.targets.title", params.inputQuery, contributorsString)
    presentation.tabText = tabCaptionText
    presentation.searchString = params.inputQuery

    val usages = mutableListOf<Usage>()
    val targets = mutableListOf<PsiElement>()
    val untilShowDoneDisposable = Disposer.newDisposable()

    val indicator = ProgressIndicatorBase()
    val tooManyUsagesStatus = TooManyUsagesStatus.createFor(indicator)
    try {
      withModalProgress(ModalTaskOwner.project(project), tabCaptionText, TaskCancellation.cancellable()) {
        indicator.start()
        providerIds.forEach { providerId ->
          providersHolder.get(providerId, isAllTab)?.getRawItemsWithOperationLifetime(params, untilShowDoneDisposable)?.collect { item ->
            indicator.checkCanceled()

            val element = when (item) {
              is SeAdaptedItem -> item.rawObject
              is SeTargetItem -> item.legacyItem.item
              is SeTextSearchItem -> item.item
              else -> null
            }

            when (element) {
              is UsageInfo2UsageAdapter -> usages.add(element)
              is SearchEverywhereItem -> usages.add(element.usage)
              else -> {
                toPsi(element)?.let { psi ->
                  val textRange = readAction { psi.textRange }
                  if (textRange != null) {
                    val usageInfo = readAction { UsageInfo(psi) }
                    usages.add(UsageInfo2UsageAdapter(usageInfo))
                  }
                  else {
                    targets.add(psi)
                  }
                }
              }
            }

            tooManyUsagesStatus.pauseProcessingIfTooManyUsages()
            if (usages.size + targets.size >= UsageLimitUtil.getSearchResultLimit() && tooManyUsagesStatus.switchTooManyUsagesStatus()) {
              UsageViewManagerImpl.showTooManyUsagesWarningLater(project, tooManyUsagesStatus, indicator, null, null, null)
            }
          }
        }
      }
    }
    catch (e: CancellationException) {
      if (!currentCoroutineContext().isActive) {
        throw e
      }
      SeLog.log { "$tabCaptionText was cancelled" }
      Disposer.dispose(untilShowDoneDisposable)
    }

    val targetsArray = if (targets.isEmpty()) {
      UsageTarget.EMPTY_ARRAY
    }
    else {
      withContext(Dispatchers.EDT) {
        PsiElement2UsageTargetAdapter.convert(
          PsiUtilCore.toPsiElementArray(targets),
          true
        )
      }
    }
    val usagesArray = usages.toArray(Usage.EMPTY_ARRAY)

    withContext(Dispatchers.EDT) {
      val instance = UsageViewManager.getInstance(projectId.findProject())
      if (instance !is UsageViewManagerWithUsageViewFactoryCallback) {
        LOG.warn("Rider show in find usages won't work!")
        try {
          instance.showUsages(targetsArray, usagesArray, presentation)
        }
        finally {
          Disposer.dispose(untilShowDoneDisposable)
        }
        return@withContext
      }

      instance.showUsages(targetsArray, usagesArray, presentation, null, Runnable { Disposer.dispose(untilShowDoneDisposable) })
    }
  }
}