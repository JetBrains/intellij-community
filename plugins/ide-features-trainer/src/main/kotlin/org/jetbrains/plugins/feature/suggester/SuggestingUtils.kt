@file:Suppress("MatchingDeclarationName")
package org.jetbrains.plugins.feature.suggester

import com.intellij.internal.statistic.local.ActionsLocalSummary
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpoint
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.plugins.feature.suggester.actions.Action
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.IOException

data class TextFragment(val startOffset: Int, val endOffset: Int, val text: String)

internal fun Editor.getSelection(): TextFragment? {
    with(selectionModel) {
        return if (selectedText != null) {
            TextFragment(selectionStart, selectionEnd, selectedText!!)
        } else {
            null
        }
    }
}

internal fun handleAction(project: Project, action: Action) {
    project.getService(FeatureSuggestersManager::class.java)
        ?.actionPerformed(action)
}

internal inline fun <reified T : PsiElement> PsiElement.getParentOfType(): T? {
    return PsiTreeUtil.getParentOfType(this, T::class.java)
}

internal fun PsiElement.getParentByPredicate(predicate: (PsiElement) -> Boolean): PsiElement? {
    return parents.find(predicate)
}

internal fun Transferable.asString(): String? {
    return try {
        getTransferData(DataFlavor.stringFlavor) as? String
    } catch (ex: IOException) {
        null
    } catch (ex: UnsupportedFlavorException) {
        null
    }
}

internal fun findBreakpointOnPosition(project: Project, position: XSourcePosition): XBreakpoint<*>? {
    val breakpointManager = XDebuggerManager.getInstance(project)?.breakpointManager ?: return null
    return breakpointManager.allBreakpoints.find {
        XSourcePosition.isOnTheSameLine(
            it.sourcePosition,
            position
        )
    }
}

@Suppress("UnstableApiUsage")
internal fun actionsLocalSummary(): ActionsLocalSummary {
    return ApplicationManager.getApplication().getService(ActionsLocalSummary::class.java)
}

internal fun getShortcutText(actionId: String): String {
    val shortcut = KeymapUtil.getShortcutText(actionId)
    return if (shortcut == "<no shortcut>") {
        "You can bind this action to convenient shortcut."
    } else {
        "Shortcut: $shortcut"
    }
}

internal fun isRedoOrUndoRunning(): Boolean {
    val commandName = CommandProcessor.getInstance().currentCommandName
    return commandName != null && (commandName.startsWith("Redo") || commandName.startsWith("Undo"))
}
