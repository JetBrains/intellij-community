package com.intellij.dev.psiViewer.properties

import com.intellij.dev.psiViewer.DevPsiViewerBundle
import com.intellij.dev.psiViewer.properties.tree.PsiViewerPropertiesTree
import com.intellij.dev.psiViewer.properties.tree.PsiViewerPropertiesTreeViewModel
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.panels.Wrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import javax.swing.JLabel

class PsiViewerPropertiesTab(
  private val viewModel: PsiViewerPropertiesTabViewModel,
  scope: CoroutineScope
) {
  val component = Wrapper()

  init {
    val treesCache = mutableMapOf<PsiViewerPropertiesTreeViewModel, PsiViewerPropertiesTree>()
    scope.launch(Dispatchers.EDT) {
      viewModel.currentTreeViewModel.collect { currentTreeVM ->
        if (currentTreeVM == null) {
          component.setContent(JLabel(DevPsiViewerBundle.message("properties.tree.no.selection")))
          return@collect
        }

        val newTreeDisposable = Disposer.newDisposable()
        val newTree = PsiViewerPropertiesTree(currentTreeVM, newTreeDisposable)
        val currentTree = treesCache.computeIfAbsent(currentTreeVM) {
          newTree
        }
        if (newTree !== currentTree) {
          Disposer.dispose(newTreeDisposable)
          component.setContent(currentTree.component)
          return@collect
        }

        // cleanup UI (dispose, remove from cache) when VM is cancelled
        currentTreeVM.scope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          try {
            awaitCancellation()
          }
          finally {
            treesCache.remove(currentTreeVM)
            Disposer.dispose(newTreeDisposable)
          }
        }
        component.setContent(currentTree.component)
      }
    }
  }
}