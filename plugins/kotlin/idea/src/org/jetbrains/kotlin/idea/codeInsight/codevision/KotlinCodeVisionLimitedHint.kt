// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.codevision

import com.intellij.codeInsight.hints.settings.showInlaySettings
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.codevision.KotlinCodeVisionUsagesCollector.Companion.CLASS_LOCATION
import org.jetbrains.kotlin.idea.codeInsight.codevision.KotlinCodeVisionUsagesCollector.Companion.FUNCTION_LOCATION
import org.jetbrains.kotlin.idea.codeInsight.codevision.KotlinCodeVisionUsagesCollector.Companion.INTERFACE_LOCATION
import org.jetbrains.kotlin.idea.codeInsight.codevision.KotlinCodeVisionUsagesCollector.Companion.PROPERTY_LOCATION
import org.jetbrains.kotlin.idea.codeInsight.codevision.KotlinCodeVisionUsagesCollector.Companion.logInheritorsClicked
import org.jetbrains.kotlin.idea.codeInsight.codevision.KotlinCodeVisionUsagesCollector.Companion.logSettingsClicked
import org.jetbrains.kotlin.idea.codeInsight.codevision.KotlinCodeVisionUsagesCollector.Companion.logUsagesClicked
import org.jetbrains.kotlin.idea.highlighter.markers.OVERRIDDEN_FUNCTION
import org.jetbrains.kotlin.idea.highlighter.markers.OVERRIDDEN_PROPERTY
import org.jetbrains.kotlin.idea.highlighter.markers.SUBCLASSED_CLASS
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import java.awt.event.MouseEvent

abstract class KotlinCodeVisionHint(hintKey: String) {
    open val regularText: String = KotlinBundle.message(hintKey)

    abstract fun onClick(editor: Editor, element: PsiElement, event: MouseEvent?)
}

abstract class KotlinCodeVisionLimitedHint(num: Int, limitReached: Boolean, regularHintKey: String, tooManyHintKey: String) :
    KotlinCodeVisionHint(regularHintKey) {

    override val regularText: String =
        if (limitReached) KotlinBundle.message(tooManyHintKey, num) else KotlinBundle.message(regularHintKey, num)
}

private const val IMPLEMENTATIONS_KEY = "hints.codevision.implementations.format"
private const val IMPLEMENTATIONS_TO_MANY_KEY = "hints.codevision.implementations.too_many.format"

private const val INHERITORS_KEY = "hints.codevision.inheritors.format"
private const val INHERITORS_TO_MANY_KEY = "hints.codevision.inheritors.to_many.format"

private const val OVERRIDES_KEY = "hints.codevision.overrides.format"
private const val OVERRIDES_TOO_MANY_KEY = "hints.codevision.overrides.to_many.format"

private const val USAGES_KEY = "hints.codevision.usages.format"
private const val USAGES_TOO_MANY_KEY = "hints.codevision.usages.too_many.format"

private const val SETTINGS_FORMAT = "hints.codevision.settings"

class Usages(usagesNum: Int, limitReached: Boolean) :
    KotlinCodeVisionLimitedHint(usagesNum, limitReached, USAGES_KEY, USAGES_TOO_MANY_KEY) {

    override fun onClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
        logUsagesClicked(editor.project)
        GotoDeclarationAction.startFindUsages(editor, editor.project!!, element)
    }
}

class FunctionOverrides(overridesNum: Int, limitReached: Boolean) :
    KotlinCodeVisionLimitedHint(overridesNum, limitReached, OVERRIDES_KEY, OVERRIDES_TOO_MANY_KEY) {

    override fun onClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
        logInheritorsClicked(editor.project, FUNCTION_LOCATION)
        val navigationHandler = OVERRIDDEN_FUNCTION.navigationHandler
        navigationHandler.navigate(event, (element as KtFunction).nameIdentifier)
    }
}

class FunctionImplementations(implNum: Int, limitReached: Boolean) :
    KotlinCodeVisionLimitedHint(implNum, limitReached, IMPLEMENTATIONS_KEY, IMPLEMENTATIONS_TO_MANY_KEY) {

    override fun onClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
        logInheritorsClicked(editor.project, FUNCTION_LOCATION)
        val navigationHandler = OVERRIDDEN_FUNCTION.navigationHandler
        navigationHandler.navigate(event, (element as KtFunction).nameIdentifier)
    }
}

class PropertyOverrides(overridesNum: Int, limitReached: Boolean) :
    KotlinCodeVisionLimitedHint(overridesNum, limitReached, OVERRIDES_KEY, OVERRIDES_TOO_MANY_KEY) {

    override fun onClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
        logInheritorsClicked(editor.project, PROPERTY_LOCATION)
        val navigationHandler = OVERRIDDEN_PROPERTY.navigationHandler
        navigationHandler.navigate(event, (element as KtProperty).nameIdentifier)
    }
}

class ClassInheritors(inheritorsNum: Int, limitReached: Boolean) :
    KotlinCodeVisionLimitedHint(inheritorsNum, limitReached, INHERITORS_KEY, INHERITORS_TO_MANY_KEY) {

    override fun onClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
        logInheritorsClicked(editor.project, CLASS_LOCATION)
        val navigationHandler = SUBCLASSED_CLASS.navigationHandler
        navigationHandler.navigate(event, (element as KtClass).nameIdentifier)
    }
}

class InterfaceImplementations(implNum: Int, limitReached: Boolean) :
    KotlinCodeVisionLimitedHint(implNum, limitReached, IMPLEMENTATIONS_KEY, IMPLEMENTATIONS_TO_MANY_KEY) {

    override fun onClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
        logInheritorsClicked(editor.project, INTERFACE_LOCATION)
        val navigationHandler = SUBCLASSED_CLASS.navigationHandler
        navigationHandler.navigate(event, (element as KtClass).nameIdentifier)
    }
}

class SettingsHint : KotlinCodeVisionHint(SETTINGS_FORMAT) {
  override fun onClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
    val project = element.project
    logSettingsClicked(project)
    showInlaySettings(project, element.language, null)
  }
}