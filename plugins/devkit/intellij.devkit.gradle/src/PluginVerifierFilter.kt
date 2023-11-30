// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.gradle

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.hint.HintManager
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.ide.DataManager
import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.browsers.OpenUrlHyperlinkInfo
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.DumbModeBlockedFunctionality
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScopes
import com.intellij.ui.JBColor
import com.intellij.util.PsiNavigateUtil
import java.awt.Font
import java.io.File
import java.util.function.Consumer

internal class PluginVerifierFilter(private val project: Project) : Filter {

  private val VERIFICATION_REPORTS_DIRECTORY = "Verification reports directory: "
  private val READING_PLUGIN_FROM = "Reading plugin to check from "
  private val READING_IDE_FROM = "Reading IDE from "

  private val DYNAMIC_PLUGIN_PASS = "Plugin can probably be enabled or disabled without IDE restart"
  private val DYNAMIC_PLUGIN_FAIL = "Plugin probably cannot be enabled or disabled without IDE restart:"
  private val DYNAMIC_PLUGIN_URL = "https://plugins.jetbrains.com/docs/intellij/dynamic-plugins.html"

  private val VERIFICATION_REPORTS = "Verification reports for "
  private val VERIFICATION_SAVED_TO = " saved to "

  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    if (line.startsWith("Plugin ") && line.contains(" against ")) {
      val initialOffset = entireLength - line.length
      val idx = line.indexOf(": ")
      if (idx == -1) return null

      return Filter.Result(initialOffset, initialOffset + idx + 1, null,
                           TextAttributes(JBColor.CYAN, null, JBColor.CYAN, EffectType.BOLD_DOTTED_LINE, Font.BOLD))
    }

    if (line.startsWith(VERIFICATION_REPORTS_DIRECTORY)) {
      return fileOrDirectoryLink(line, entireLength, VERIFICATION_REPORTS_DIRECTORY)
    }
    if (line.contains(READING_PLUGIN_FROM)) {
      return fileOrDirectoryLink(line, entireLength, READING_PLUGIN_FROM)
    }
    if (line.contains(READING_IDE_FROM)) {
      return fileOrDirectoryLink(line, entireLength, READING_IDE_FROM)
    }
    if (line.contains(VERIFICATION_REPORTS) && line.contains(VERIFICATION_SAVED_TO)) {
      return fileOrDirectoryLink(line,entireLength, VERIFICATION_SAVED_TO)
    }

    if (StringUtil.contains(line, DYNAMIC_PLUGIN_PASS)) {
      return coloredWebLink(line, entireLength, DYNAMIC_PLUGIN_PASS, JBColor.GREEN, DYNAMIC_PLUGIN_URL)
    }
    if (StringUtil.contains(line, DYNAMIC_PLUGIN_FAIL)) {
      return coloredWebLink(line, entireLength, DYNAMIC_PLUGIN_FAIL, JBColor.RED, DYNAMIC_PLUGIN_URL)
    }

    if (!line.contains("in ")) {
      return null
    }

    return codeLocationLink(line, entireLength, "is invoked in ") ?: codeLocationLink(line, entireLength, "is accessed in ")
           ?: codeLocationLink(line, entireLength, "is overridden in class ") ?: codeLocationLink(line, entireLength, "is referenced in ")
  }

  private fun fileOrDirectoryLink(line: String, entireLength: Int, text: String): Filter.Result {
    val initialOffset = entireLength - line.length
    val idx = line.indexOf(text) + text.length
    val path = line.substring(idx, line.length - 1)

    return Filter.Result(initialOffset + idx, initialOffset + line.length, HyperlinkInfo {
      val file = File(path)
      if (!file.exists()) {
        showErrorHint(CodeInsightBundle.message("declaration.navigation.nowhere.to.go"))
        return@HyperlinkInfo
      }

      if (file.isDirectory()) {
        RevealFileAction.openDirectory(file)
      }
      else {
        RevealFileAction.openFile(file)
      }
    })
  }

  private fun coloredWebLink(line: String, entireLength: Int, text: String, color: JBColor, linkUrl: String): Filter.Result {
    val idx = line.indexOf(text)
    val initialOffset = entireLength - line.length

    val textAttributes = EditorColorsManager.getInstance().globalScheme.getAttributes(CodeInsightColors.HYPERLINK_ATTRIBUTES).clone()
    textAttributes.foregroundColor = color

    return Filter.Result(initialOffset + idx, initialOffset + idx + text.length,
                         OpenUrlHyperlinkInfo(linkUrl),
                         textAttributes, textAttributes)
  }

  private fun codeLocationLink(line: String, entireLength: Int, prefixText: String): Filter.Result? {
    if (!StringUtil.contains(line, prefixText)) return null

    val idx = line.indexOf(prefixText) + prefixText.length
    val initialOffset = entireLength - line.length
    val substring = line.substring(idx, line.length - 1)

    val hasColon = substring.contains(':')

    val codeSignature = when {
      hasColon -> substring.substringBefore(" : ")
      else -> substring
    }
    val hasParen = codeSignature.contains('(')

    val lastDotIdx = when {
      hasParen -> codeSignature.substring(0, StringUtil.indexOf(codeSignature, '(')).lastIndexOf('.')
      else -> codeSignature.lastIndexOf('.')
    }
    val classname = when {
      hasColon || hasParen -> codeSignature.substring(0, lastDotIdx)
      else -> codeSignature
    }

    val methodName: String? = when {
      hasParen -> codeSignature.substring(lastDotIdx + 1, codeSignature.lastIndexOf('('))
      else -> null
    }

    return Filter.Result(initialOffset + idx, initialOffset + idx + codeSignature.length, CodeLocationHyperlinkInfo(classname, methodName))
  }

  inner class CodeLocationHyperlinkInfo(private val classname: String, private val methodName: String?) : HyperlinkInfo {
    override fun navigate(it: Project) {
      if (DumbService.isDumb(project)) {
        DumbService.getInstance(project).showDumbModeNotificationForFunctionality(
          CodeInsightBundle.message("message.navigation.is.not.available.here.during.index.update"),
          DumbModeBlockedFunctionality.GotoDeclarationOnly)
        return
      }

      val navigationTarget = getNavigationTarget()
      if (navigationTarget == null) {
        showErrorHint(CodeInsightBundle.message("declaration.navigation.nowhere.to.go"))
        return
      }

      PsiNavigateUtil.navigate(navigationTarget)
    }

    fun getNavigationTarget(): PsiElement? {
      fun findClassInProjectScope(fqn: String): PsiClass? {
        return JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScopes.projectProductionScope(project))
      }

      val outerClassName = classname.substringBefore("$")
      var psiClass = findClassInProjectScope(outerClassName)
      if (psiClass == null) {
        // fallback for FQN.methodName$1$3$2.method(Param) // FQN.lambda$methodName$1(Param)
        psiClass = findClassInProjectScope(outerClassName.substringBeforeLast('.'))
      }
      if (psiClass == null) return null

      if (methodName != null) {
        val psiMethod = psiClass.findMethodsByName(methodName, false).firstOrNull()
        if (psiMethod != null) {
          return psiMethod
        }
      }

      return psiClass
    }
  }

  private fun showErrorHint(@NlsContexts.HintText text: String) {
    DataManager.getInstance()
      .getDataContextFromFocusAsync()
      .onSuccess(Consumer {
        val editor: Editor = CommonDataKeys.EDITOR.getData(it) ?: return@Consumer

        HintManager.getInstance().showErrorHint(editor, text)
      }
      )
  }
}