// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.psi.impl.PropertyImpl
import com.intellij.lang.properties.psi.impl.PropertyKeyImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.IntelliJProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.IncorrectOperationException
import com.intellij.util.PsiNavigateUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.devkit.DevKitBundle
import java.util.*

@NonNls
internal const val REGISTRY_PROPERTIES_FILENAME = "registry.properties"

@NonNls
internal const val DESCRIPTION_SUFFIX = ".description"

@NonNls
internal const val RESTART_REQUIRED_SUFFIX = ".restartRequired"

internal fun isImplicitUsageKey(keyName: String): Boolean {
  return keyName.endsWith(DESCRIPTION_SUFFIX) ||
         keyName.endsWith(RESTART_REQUIRED_SUFFIX)
}

internal fun isRegistryPropertiesFile(psiFile: PsiFile): Boolean {
  return IntelliJProjectUtil.isIntelliJPlatformProject(psiFile.project) && psiFile.name == REGISTRY_PROPERTIES_FILENAME
}

/**
 * Highlights key in `registry.properties` without matching `key.description` entry + corresponding quickfix.
 */
private class RegistryPropertiesAnnotator : Annotator, DumbAware {
  @NonNls
  private val PLUGIN_GROUP_NAMES = setOf(
    "appcode", "cidr", "clion",
    "cvs", "git", "github", "svn", "hg4idea", "tfs",
    "dart", "markdown",
    "java", "javac", "uast", "junit4", "dsm",
    "js", "javascript", "typescript", "nodejs", "eslint", "jest",
    "ruby", "rubymine",
    "groovy", "grails", "python", "php",
    "kotlin", "spring", "jupyter", "dataspell", "javafx",
    "maven", "gradle", "android", "eclipse"
  )

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element !is PropertyKeyImpl) return

    val file = holder.currentAnnotationSession.file
    if (!isRegistryPropertiesFile(file)) {
      return
    }

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
        propertyName.startsWith("execution.java.") ||
        propertyName.startsWith("debugger.log.jdi.")) {

      holder.newAnnotation(HighlightSeverity.ERROR, DevKitBundle.message("registry.properties.annotator.plugin.keys.use.ep"))
        .withFix(ShowEPDeclarationIntention(propertyName)).create()
    }

    val propertiesFile = file as PropertiesFile
    val descriptionProperty = propertiesFile.findPropertyByKey(propertyName + DESCRIPTION_SUFFIX)
    if (descriptionProperty == null) {
      holder.newAnnotation(HighlightSeverity.WARNING,
                           DevKitBundle.message("registry.properties.annotator.key.no.description.key", propertyName))
        .withFix(AddDescriptionKeyIntention(propertyName)).create()
    }
  }

  private class ShowEPDeclarationIntention(private val propertyName: String) : IntentionAction, DumbAware {
    override fun startInWriteAction(): Boolean = false

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
      return IntentionPreviewInfo.EMPTY
    }

    override fun getFamilyName(): String = DevKitBundle.message("registry.properties.annotator.show.ep.family.name")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun getText(): String = DevKitBundle.message("registry.properties.annotator.show.ep.name", propertyName)

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
      val propertiesFile = file as PropertiesFile
      val defaultValue = propertiesFile.findPropertyByKey(propertyName)!!.value
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
  }

  private class AddDescriptionKeyIntention(private val myPropertyName: String) : IntentionAction, DumbAware {

    @Nls
    override fun getText(): String = DevKitBundle.message("registry.properties.annotator.add.description.text", myPropertyName)

    @Nls
    override fun getFamilyName(): String = DevKitBundle.message("registry.properties.annotator.add.description.family.name")

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

    @Throws(IncorrectOperationException::class)
    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
      val propertiesFile = file as PropertiesFile

      val originalProperty = propertiesFile.findPropertyByKey(myPropertyName) as PropertyImpl?
      val descriptionProperty = propertiesFile.addPropertyAfter(myPropertyName + DESCRIPTION_SUFFIX, "Description", originalProperty)

      val valueNode = (descriptionProperty.psiElement as PropertyImpl).valueNode!!
      if (!IntentionPreviewUtils.isPreviewElement(valueNode.psi)) {
        PsiNavigateUtil.navigate(valueNode.psi)
      }
    }

    override fun startInWriteAction(): Boolean = true
  }

}