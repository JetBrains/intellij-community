/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.inspections

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.psi.impl.PropertyImpl
import com.intellij.lang.properties.psi.impl.PropertyKeyImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.IncorrectOperationException
import com.intellij.util.PsiNavigateUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.devkit.util.PsiUtil

/**
 * Highlights key in `registry.properties` without matching `key.description` entry + corresponding quickfix.
 */
class RegistryPropertiesAnnotator : Annotator {

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

    val propertiesFile = file as PropertiesFile
    val descriptionProperty = propertiesFile.findPropertyByKey(propertyName + DESCRIPTION_SUFFIX)
    if (descriptionProperty == null) {
      holder.createWarningAnnotation(element.node, "Key '$propertyName' does not have description key")
        .registerFix(AddDescriptionKeyIntention(propertyName))
    }
  }

  private class AddDescriptionKeyIntention(private val myPropertyName: String) : IntentionAction {

    @Nls
    override fun getText(): String = "Add description key for '$myPropertyName'"

    @Nls
    override fun getFamilyName(): String = "Add description key"

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true

    @Throws(IncorrectOperationException::class)
    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
      val propertiesFile = file as PropertiesFile

      val originalProperty = propertiesFile.findPropertyByKey(myPropertyName) as PropertyImpl?
      val descriptionProperty = propertiesFile.addPropertyAfter(myPropertyName + DESCRIPTION_SUFFIX, "Description", originalProperty)

      val valueNode = (descriptionProperty.psiElement as PropertyImpl).valueNode!!
      PsiNavigateUtil.navigate(valueNode.psi)
    }

    override fun startInWriteAction(): Boolean = true
  }

  companion object {

    @NonNls private const val REGISTRY_PROPERTIES_FILENAME = "registry.properties"

    @NonNls private const val DESCRIPTION_SUFFIX = ".description"

    @NonNls private const val RESTART_REQUIRED_SUFFIX = ".restartRequired"

    @JvmStatic
    fun isImplicitUsageKey(keyName: String): Boolean =
      StringUtil.endsWith(keyName, DESCRIPTION_SUFFIX) || StringUtil.endsWith(keyName, RESTART_REQUIRED_SUFFIX)

    @JvmStatic
    fun isRegistryPropertiesFile(psiFile: PsiFile): Boolean =
      PsiUtil.isIdeaProject(psiFile.project) && psiFile.name == REGISTRY_PROPERTIES_FILENAME
  }
}