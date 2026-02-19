// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package training.featuresSuggester

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parents
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import training.featuresSuggester.actions.Action
import training.featuresSuggester.settings.FeatureSuggesterSettings
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.IOException

data class TextFragment(val startOffset: Int, val endOffset: Int, val text: String)

internal object SuggestingUtils {
  var forceShowSuggestions: Boolean
    get() = Registry.`is`("feature.suggester.force.show.suggestions", false)
    set(value) = Registry.get("feature.suggester.force.show.suggestions").setValue(value)

  fun isActionsProcessingEnabled(project: Project): Boolean {
    return !project.isDisposed && !DumbService.isDumb(project) && FeatureSuggesterSettings.instance().isAnySuggesterEnabled
  }

  fun handleAction(project: Project, action: Action) {
    if (isActionsProcessingEnabled(project)) {
      project.getService(FeatureSuggestersManager::class.java)?.actionPerformed(action)
    }
  }

  fun findBreakpointOnPosition(project: Project, position: XSourcePosition): XBreakpoint<*>? {
    val breakpointManager = XDebuggerManager.getInstance(project)?.breakpointManager ?: return null
    return breakpointManager.allBreakpoints.find { b ->
      b is XLineBreakpoint<*> && b.fileUrl == position.file.url && b.line == position.line
    }
  }

  fun Transferable.asString(): String? {
    return try {
      getTransferData(DataFlavor.stringFlavor) as? String
    }
    catch (ex: IOException) {
      null
    }
    catch (ex: UnsupportedFlavorException) {
      null
    }
  }

  fun Editor.getSelection(): TextFragment? {
    with(selectionModel) {
      return if (selectedText != null) {
        TextFragment(selectionStart, selectionEnd, selectedText!!)
      }
      else {
        null
      }
    }
  }
}

inline fun <reified T : PsiElement> PsiElement.getParentOfType(): T? {
  return PsiTreeUtil.getParentOfType(this, T::class.java)
}

fun PsiElement.getParentByPredicate(predicate: (PsiElement) -> Boolean): PsiElement? {
  return parents(true).find(predicate)
}
