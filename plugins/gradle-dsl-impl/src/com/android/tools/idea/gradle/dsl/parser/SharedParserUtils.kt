/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.parser

import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement.APPLY_BLOCK_NAME
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement.EXT
import com.android.tools.idea.gradle.dsl.parser.build.SubProjectsDslElement
import com.android.tools.idea.gradle.dsl.parser.configurations.ConfigurationDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslClosure
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile
import com.android.tools.idea.gradle.dsl.parser.repositories.FlatDirRepositoryDslElement
import com.android.tools.idea.gradle.dsl.parser.repositories.MavenRepositoryDslElement
import com.android.tools.idea.gradle.dsl.parser.settings.ProjectPropertiesDslElement
import com.google.common.base.Splitter
import com.google.common.collect.Lists
import com.intellij.psi.PsiElement
import java.util.ArrayList

/**
 * Set of classes whose properties should not be merged into each other.
 */
private val makeDistinctClassSet = setOf(MavenRepositoryDslElement::class.java, FlatDirRepositoryDslElement::class.java)

/**
 * Get the block element that is given be repeat
 */
fun GradleDslFile.getBlockElement(
  nameParts: List<String>,
  converter: GradleDslNameConverter,
  parentElement: GradlePropertiesDslElement,
  nameElement: GradleNameElement? = null
): GradlePropertiesDslElement? {
  return nameParts.map { namePart -> namePart.trim { it <= ' ' } }.fold(parentElement) { resultElement, nestedElementName ->
    val canonicalNestedElementName = converter.modelNameForParent(nestedElementName, resultElement)
    val elementName = nameElement ?: GradleNameElement.fake(canonicalNestedElementName)
    var element = resultElement.getElement(canonicalNestedElementName)

    if (element != null && makeDistinctClassSet.contains(element::class.java)) {
      element = null // Force recreation of the element
    }

    if (element is GradlePropertiesDslElement) {
      return@fold element
    }

    if (element != null) return null

    // Handle special cases based on the child element name
    when (nestedElementName) {
      "rootProject" -> return@fold context.rootProjectFile ?: this
      // Ext element is supported for any Gradle domain object that implements ExtensionAware. Here we get or create
      // such an element if needed.
      EXT.name -> {
        val newElement = EXT.constructor.construct(resultElement, elementName)
        resultElement.setParsedElement(newElement)
        return@fold newElement
      }
      APPLY_BLOCK_NAME -> {
        val newApplyElement = ApplyDslElement(resultElement)
        resultElement.setParsedElement(newApplyElement)
        return@fold newApplyElement
      }
    }

    val newElement: GradlePropertiesDslElement = when (resultElement) {
      // Some parent blocks require special-case treatment
      is GradleDslFile, is SubProjectsDslElement -> createNewElementForFileOrSubProject(resultElement, nestedElementName) ?: return null
      // we're not going to be clever about the contents of a ConfigurationDslElement: but we do need
      // to record whether there's anything there or not.
      is ConfigurationDslElement -> GradleDslClosure(resultElement, null, elementName)
      // normal cases can simply construct a child block based on information in the parent Dsl element
      else -> resultElement.getChildPropertiesElementDescription(nestedElementName)?.constructor?.construct(resultElement, elementName)
              ?: return null
    }

    resultElement.setParsedElement(newElement)
    return@fold newElement
  }
}

private fun createNewElementForFileOrSubProject(resultElement: GradlePropertiesDslElement,
                                                nestedElementName: String): GradlePropertiesDslElement? {
  val elementName = GradleNameElement.fake(nestedElementName)
  return when (val properties = resultElement.getChildPropertiesElementDescription(nestedElementName)) {
    null -> {
      val projectKey = ProjectPropertiesDslElement.getStandardProjectKey(nestedElementName) ?: return null
      ProjectPropertiesDslElement(resultElement, GradleNameElement.fake(projectKey))
    }
    else -> properties.constructor.construct(resultElement, elementName)
  }
}

/**
 * Get the parent dsl element with a valid psi
 */
internal fun getNextValidParent(element: GradleDslElement): GradleDslElement? {
  var element : GradleDslElement? = element
  var psi = element?.psiElement
  while (element != null && (psi == null || !psi.isValid)) {
    element = element.parent ?: return element

    psi = element.psiElement
  }

  return element
}

internal fun removePsiIfInvalid(element: GradleDslElement?) {
  if (element == null) return

  if (element.psiElement != null && !element.psiElement!!.isValid) {
    element.psiElement = null
  }

  if (element.parent != null) {
    removePsiIfInvalid(element.parent)
  }
}

/**
 * @param startElement starting element
 * @return the last non-null psi element in the tree starting at node startElement.
 */
internal fun findLastPsiElementIn(startElement: GradleDslElement): PsiElement? {
  val psiElement = startElement.psiElement
  if (psiElement != null) {
    return psiElement
  }

  for (element in Lists.reverse(ArrayList(startElement.children))) {
    if (element != null) {
      val psi = findLastPsiElementIn(element)
      if (psi != null) {
        return psi
      }
    }
  }
  return null
}

/**
 * Get the external name of a dsl element by trimming the parent's name parts and converting the name from model to external, if necessary,
 * returning a pair of the name and whether this is a method call an assignment or unknown (see
 * [GradleDslNameConverter.externalNameForParent])
 */
internal fun maybeTrimForParent(name: GradleNameElement,
                                parent: GradleDslElement?,
                                converter: GradleDslNameConverter): Pair<String, Boolean?> {
  // FIXME(xof): this case needs fixing too
  if (parent == null) return name.fullName() to null

  val parts = ArrayList(name.fullNameParts())
  if (parts.isEmpty()) {
    return name.fullName() to null
  }
  var lastNamePart = parts.removeAt(parts.size - 1)
  val parentParts = Splitter.on(".").splitToList(parent.qualifiedName)
  var i = 0
  while (i < parentParts.size && !parts.isEmpty() && parentParts[i] == parts[0]) {
    parts.removeAt(0)
    i++
  }

  val externalNameInfo = converter.externalNameForParent(lastNamePart, parent)

  lastNamePart = externalNameInfo.first
  parts.add(lastNamePart)
  return GradleNameElement.createNameFromParts(parts) to externalNameInfo.second
}
