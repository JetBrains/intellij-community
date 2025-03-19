// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.references

import com.intellij.patterns.PsiJavaPatterns.psiMethod
import com.intellij.patterns.uast.UDeclarationPattern
import com.intellij.patterns.uast.UExpressionPattern
import com.intellij.patterns.uast.injectionHostUExpression
import com.intellij.patterns.uast.uExpression
import com.intellij.psi.*
import com.intellij.util.ThreeState
import org.jetbrains.idea.devkit.util.PsiUtil
import org.jetbrains.uast.*

internal class ActionOrGroupIdReferenceContributor : PsiReferenceContributor() {

  override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    // Action/Group
    registerReference(
      registrar, ThreeState.UNSURE,

      uExpression().inside(false, UDeclarationPattern(UField::class.java).filter {
        val containingClass = it.getContainingUClass() ?: return@filter false
        val fqn = containingClass.qualifiedName
        return@filter fqn == "com.intellij.xdebugger.impl.actions.XDebuggerActions" ||
                      fqn == "com.intellij.openapi.vcs.VcsActions" ||
                      fqn == "com.intellij.vcs.log.ui.VcsLogActionIds"
      }),

      uExpression().inside(false, UDeclarationPattern(UField::class.java).filter {
        it.getAsJavaPsiElement(PsiField::class.java)?.name == "ACTION_ID" && it.type.equalsToText(CommonClassNames.JAVA_LANG_STRING)
      }),

      registerMethodCallParameter("com.intellij.openapi.actionSystem.ActionManager", 0,
                                  "getAction", "registerAction", "unregisterAction", "replaceAction",
                                  "isGroup", "getActionOrStub", "getKeyboardShortcut"),

      registerMethodCallParameter("com.intellij.openapi.actionSystem.ex.ActionRuntimeRegistrar", 0,
                                  "registerAction", "unregisterAction", "getActionOrStub", "getUnstubbedAction", "replaceAction"),

      registerMethodCallParameter("com.intellij.openapi.actionSystem.ex.ActionUtil", 0,
                                  "getUnavailableMessage", "getActionUnavailableMessage", "createActionListener", "wrap", "getShortcutSet", "getAction"),
      registerMethodCallParameter("com.intellij.openapi.actionSystem.ex.ActionUtil", 1,
                                  "copyFrom", "mergeFrom"),

      registerMethodCallParameter("com.intellij.openapi.actionSystem.AbbreviationManager", 0,
                                  "getAbbreviations", "removeAllAbbreviations"),
      registerMethodCallParameter("com.intellij.openapi.actionSystem.AbbreviationManager", 1,
                                  "register", "remove"),

      registerMethodCallParameter("com.intellij.openapi.keymap.Keymap", 0,
                                  "getShortcuts", "addShortcut", "removeShortcut", "getConflicts", "removeAllActionShortcuts", "hasActionId"),

      registerMethodCallParameter("com.intellij.openapi.keymap.KeymapUtil", 0,
                                  "getShortcutText", "getShortcutTextOrNull", "getActiveKeymapShortcuts",
                                  "getPrimaryShortcut", "getFirstKeyboardShortcutText", "getFirstMouseShortcutText"),
      registerMethodCallParameter("com.intellij.openapi.keymap.KeymapUtil", 1, "isEventForAction", "createTooltipText"),
      registerMethodCallParameter("com.intellij.openapi.keymap.KeymapUtil", 2, "matchActionMouseShortcutsModifiers"),

      registerMethodCallParameter("com.intellij.ui.EditorNotificationPanel", 1,
                                  "createActionLabel"),

      registerMethodCallParameter("com.intellij.openapi.editor.actionSystem.EditorActionManager", 0,
                                  "getActionHandler", "setActionHandler"),
      registerMethodCallParameter("com.intellij.openapi.fileEditor.impl.EditorEmptyTextPainter", 0,
                                  "getActionShortcutText"),

      // testing
      registerMethodCallParameter("com.intellij.driver.sdk.ActionManagerKt", 1,
                                  "invokeAction", "invokeGlobalBackendAction"),

      )

    // Group
    registerReference(
      registrar, ThreeState.NO,

      registerMethodCallParameter("com.intellij.openapi.actionSystem.ex.ActionUtil", 0,
                                  "getActionGroup"),

      registerMethodCallParameter("com.intellij.idea.ActionsBundle", 0,
                                  "groupText"),

      uExpression().inside(false, UDeclarationPattern(UField::class.java).filter {
        val name = it.getAsJavaPsiElement(PsiField::class.java)?.name ?: return@filter false
        return@filter name.startsWith("GROUP_") && it.getContainingUClass()?.qualifiedName == "com.intellij.openapi.actionSystem.IdeActions"
      })
    )

    // Action
    registerReference(
      registrar, ThreeState.YES,

      registerMethodCallParameter("com.intellij.idea.ActionsBundle", 0,
                                  "actionText", "actionDescription"),

      registerMethodCallParameter("com.intellij.testFramework.fixtures.EditorTestFixture", 0,
                                  "performEditorAction"),
      registerMethodCallParameter("com.intellij.testFramework.fixtures.CodeInsightTestFixture", 0,
                                  "performEditorAction"),
      registerMethodCallParameter("com.intellij.testFramework.EditorTestUtil", 1,
                                  "executeAction"),
      registerMethodCallParameter("com.intellij.testFramework.LightPlatformCodeInsightTestCase",0,
                                  "executeAction"),
      registerMethodCallParameter("com.intellij.testFramework.PlatformTestUtil",0,
                                  "invokeNamedAction"),

      uExpression().inside(false, UDeclarationPattern(UField::class.java).filter {
        val name = it.getAsJavaPsiElement(PsiField::class.java)?.name ?: return@filter false
        return@filter name.startsWith("ACTION_") && it.getContainingUClass()?.qualifiedName == "com.intellij.openapi.actionSystem.IdeActions"
      })
    )
  }

  private fun registerReference(registrar: PsiReferenceRegistrar, isAction: ThreeState, vararg filters: com.intellij.patterns.ElementPattern<*>) {
    registrar.registerUastReferenceProvider(
      injectionHostUExpression()
        .sourcePsiFilter { PsiUtil.isPluginProject(it.project) }
        .andOr(*filters),
      uastInjectionHostReferenceProvider { uExpression, host ->
        arrayOf(ActionOrGroupIdReference(
          host,
          ElementManipulators.getValueTextRange(host),
          uExpression.evaluateString() ?: ElementManipulators.getValueText(host),
          isAction
        ))
      },
      PsiReferenceRegistrar.HIGHER_PRIORITY
    )
  }

  private fun registerMethodCallParameter(classFqn: String, parameterIndex: Int, vararg methodNames: String): UExpressionPattern.Capture<UExpression> {
    return uExpression().methodCallParameter(parameterIndex, psiMethod().withName(*methodNames).definedInClass(classFqn))
  }

}
