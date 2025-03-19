package com.intellij.dev.psiViewer.properties

import com.intellij.dev.psiViewer.PsiViewerSettings
import com.intellij.dev.psiViewer.properties.tree.PsiViewerPropertiesTreeViewModel
import com.intellij.dev.psiViewer.properties.tree.PsiViewerPropertyNode
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.asContextElement
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.coroutineSuspender
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.descendants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.function.Consumer

@OptIn(ExperimentalCoroutinesApi::class)
class PsiViewerPropertiesTabViewModel(
  project: Project,
  _scope: CoroutineScope,
  settings: PsiViewerSettings,
  private val psiElementInMainTreeSelector: Consumer<PsiElement>
) {
  private val coroutineSuspender = coroutineSuspender(active = false)

  private val scope: CoroutineScope = _scope + coroutineSuspender.asContextElement()

  private class ContextHolder(val context: PsiViewerPropertyNode.Context)

  private val currentContext = MutableStateFlow(ContextHolder(PsiViewerPropertyNode.Context(project, settings.showEmptyProperties, this::getPsiSelectorInMainTree)))

  private val selectedPsiElement = MutableStateFlow<PsiElement?>(null)

  val currentTreeViewModel: StateFlow<PsiViewerPropertiesTreeViewModel?>

  private val allNodesInCurrentPsiFile: SharedFlow<Pair<PsiFile, Set<PsiElement>>?>

  val showEmptyProperties: Boolean
    get() = currentContext.value.context.showEmptyNodes

  init {
    scope.launch(Dispatchers.Default) {
      currentContext
        .map { it.context.showEmptyNodes }
        .distinctUntilChanged()
        .collect {
          settings.showEmptyProperties = it
        }
    }

    currentTreeViewModel = currentContext
      .transformLatest { contextHolder ->
        checkCanceled()
        val emitter = this
        supervisorScope {
          val treesVMsScope = this
          val treesVMsCache = mutableMapOf<PsiElement, PsiViewerPropertiesTreeViewModel>()
          selectedPsiElement.collect { psiElement ->
            checkCanceled()
            if (psiElement == null) {
              emitter.emit(null)
              return@collect
            }

            val psiElementString = readAction {
              psiElement.toString()
            }
            val treeVM = treesVMsCache.computeIfAbsent(psiElement) {
              PsiViewerPropertiesTreeViewModel(psiElement, psiElementString, treesVMsScope, contextHolder.context)
            }
            emitter.emit(treeVM)
          }
        }
      }
      .stateIn(scope, SharingStarted.Lazily, null)

    allNodesInCurrentPsiFile = currentTreeViewModel
      .mapLatest {
        checkCanceled()
        readAction {
          it?.rootNode?.element?.containingFile
        }
      }
      .distinctUntilChangedBy { System.identityHashCode(it) }
      .mapLatest { currentPsiFile ->
        checkCanceled()
        val allNodesSet = currentPsiFile?.descendants()?.toSet() ?: return@mapLatest null
        currentPsiFile to allNodesSet
      }
      .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)
  }

  fun setSelectedPsiElement(element: PsiElement?) {
    selectedPsiElement.value = element
  }

  fun reset() {
    currentContext.update { ContextHolder(it.context) }
  }

  fun setShowEmptyProperties(doShow: Boolean) {
    currentContext.update { ContextHolder(it.context.copy(showEmptyNodes = doShow)) }
  }

  fun openTab() {
    coroutineSuspender.resume()
  }

  fun closeTab() {
    coroutineSuspender.pause()
  }

  private suspend fun getAllNodesInCurrentPsiFile(): Set<PsiElement> {
    val currentPsiFile = readAction {
      currentTreeViewModel.value?.rootNode?.element?.containingFile
    } ?: return emptySet()

    return allNodesInCurrentPsiFile.first { it?.first == currentPsiFile }?.second ?: return emptySet()
  }

  private suspend fun getPsiSelectorInMainTree(psiElement: PsiElement): Runnable? {
    val allCurrentNodes = getAllNodesInCurrentPsiFile()

    val isPsiElementPresentInCurrentFile = psiElement in allCurrentNodes
    if (!isPsiElementPresentInCurrentFile) return null

    return Runnable {
      scope.launch(Dispatchers.EDT) {
        psiElementInMainTreeSelector.accept(psiElement)
      }
    }
  }
}