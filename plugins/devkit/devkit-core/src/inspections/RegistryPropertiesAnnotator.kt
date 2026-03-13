// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.properties.PropertiesImplUtil
import com.intellij.lang.properties.psi.impl.PropertyImpl
import com.intellij.lang.properties.psi.impl.PropertyKeyImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.util.PsiNavigateUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.devkit.DevKitBundle
import java.util.Locale

internal const val REGISTRY_PROPERTIES_FILENAME = "registry.properties"
internal const val DESCRIPTION_SUFFIX = ".description"
internal const val RESTART_REQUIRED_SUFFIX = ".restartRequired"

internal fun isImplicitUsageKey(keyName: String): Boolean {
  return keyName.endsWith(DESCRIPTION_SUFFIX) ||
         keyName.endsWith(RESTART_REQUIRED_SUFFIX)
}

private val PLUGIN_GROUP_NAMES = setOf(
  "appcode", "cidr", "clion",
  "cvs", "git", "github", "svn", "hg4idea", "tfs",
  "dart", "markdown",
  "java", "javac", "uast", "junit4", "dsm",
  "js", "javascript", "typescript", "nodejs", "eslint", "jest",
  "ruby", "rubymine",
  "groovy", "python", "php",
  "kotlin", "spring", "jupyter", "dataspell", "javafx",
  "maven", "gradle", "android", "eclipse",
  "rider", "compiler"
)

internal fun isRegistryPropertiesFile(psiFile: PsiFile): Boolean {
  return IntelliJProjectUtil.isIntelliJPlatformProject(psiFile.project) && psiFile.name == REGISTRY_PROPERTIES_FILENAME
}

internal class RegistryPropertiesInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    if (!isRegistryPropertiesFile(holder.file)) return PsiElementVisitor.EMPTY_VISITOR

    val propertiesFile = PropertiesImplUtil.getPropertiesFile(holder.file)

    return object : PsiElementVisitor() {
      override fun visitElement(element: PsiElement) {
        if (element !is PropertyKeyImpl) return

        val propertyName = element.text
        if (isImplicitUsageKey(propertyName)) {
          return
        }

        val groupName = propertyName.substringBefore('.').lowercase(Locale.getDefault())

        if (PLUGIN_GROUP_NAMES.contains(groupName) ||
            propertyName.startsWith("editor.config.") ||
            propertyName.startsWith("debugger.kotlin.") ||
            propertyName.startsWith("debugger.enable.kotlin.") ||
            propertyName.startsWith("ide.java.") ||
            propertyName.startsWith("ide.jvm.") ||
            propertyName.startsWith("idea.profiler.") ||
            propertyName.startsWith("execution.java.") ||
            propertyName.startsWith("debugger.log.jdi.")) {

          holder.registerProblem(
            element,
            DevKitBundle.message("registry.properties.annotator.plugin.keys.use.ep"),
            ShowEPDeclarationIntention(propertyName, element)
          )
        }

        val descriptionProperty = propertiesFile?.findPropertyByKey(propertyName + DESCRIPTION_SUFFIX)
        if (descriptionProperty == null) {
          holder.registerProblem(
            element,
            DevKitBundle.message("registry.properties.annotator.key.no.description.key", propertyName),
            ProblemHighlightType.WARNING,
            AddDescriptionKeyIntention(propertyName, element)
          )
        }
      }
    }
  }
}

private class ShowEPDeclarationIntention(private val propertyName: String, element: PsiElement) : LocalQuickFixOnPsiElement(element), DumbAware {
  override fun startInWriteAction(): Boolean = false
  override fun getFamilyName(): String = DevKitBundle.message("registry.properties.annotator.show.ep.family.name")
  override fun getText(): String = DevKitBundle.message("registry.properties.annotator.show.ep.name", propertyName)

  override fun invoke(project: Project, psiFile: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    val propertiesFile = psiFile.let { PropertiesImplUtil.getPropertiesFile(it) } ?: return
    val defaultValue = propertiesFile.findPropertyByKey(propertyName)?.value
    val description = propertiesFile.findPropertyByKey(propertyName + DESCRIPTION_SUFFIX)?.value
    @NonNls var restartRequiredText = ""
    if (propertiesFile.findPropertyByKey(propertyName + RESTART_REQUIRED_SUFFIX) != null) {
      restartRequiredText = "restartRequired=\"true\""
    }

    val epText = """
        <registryKey key="${propertyName}" defaultValue="${defaultValue}" ${restartRequiredText}
                     description="${description}"/>
      """.trimIndent()
    Messages.showMultilineInputDialog(project,
                                      DevKitBundle.message("registry.properties.annotator.show.ep.message"),
                                      DevKitBundle.message("registry.properties.annotator.show.ep.title"),
                                      epText, null, null)
  }

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY
}

private class AddDescriptionKeyIntention(private val myPropertyName: String, element: PsiElement) : LocalQuickFixOnPsiElement(element), DumbAware {
  override fun startInWriteAction(): Boolean = true
  override fun getText(): @Nls String = DevKitBundle.message("registry.properties.annotator.add.description.text", myPropertyName)
  override fun getFamilyName(): @Nls String = DevKitBundle.message("registry.properties.annotator.add.description.family.name")

  override fun invoke(project: Project, psiFile: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    val propertiesFile = psiFile.let { PropertiesImplUtil.getPropertiesFile(it) } ?: return

    val originalProperty = propertiesFile.findPropertyByKey(myPropertyName)
    val descriptionProperty = propertiesFile.addPropertyAfter(myPropertyName + DESCRIPTION_SUFFIX, "Description", originalProperty)

    val psi = descriptionProperty.psiElement
    if (psi is PropertyImpl) {
      val valuePsi = psi.valueNode?.psi ?: psi
      if (!IntentionPreviewUtils.isPreviewElement(valuePsi)) {
        PsiNavigateUtil.navigate(valuePsi)
      }
    }
    else if (!IntentionPreviewUtils.isPreviewElement(psi)) {
      PsiNavigateUtil.navigate(psi)
    }
  }

  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo = IntentionPreviewInfo.EMPTY
}