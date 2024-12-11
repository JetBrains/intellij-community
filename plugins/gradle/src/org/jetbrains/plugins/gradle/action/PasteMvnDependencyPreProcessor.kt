// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.action

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RawText
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.createDocumentBuilder
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.statistics.GradleActionsUsagesCollector
import org.jetbrains.plugins.gradle.statistics.GradleActionsUsagesCollector.trigger
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.w3c.dom.Document
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.IOException
import java.io.StringReader

class PasteMvnDependencyPreProcessor : CopyPastePreProcessor {

  override fun preprocessOnCopy(file: PsiFile?, startOffsets: IntArray?, endOffsets: IntArray?, text: String?): String? {
    return null
  }

  override fun preprocessOnPaste(project: Project?, file: PsiFile, editor: Editor?, text: String, rawText: RawText?): String {
    if (isApplicable(file) && isMvnDependency(text)) {
      trigger(project, GradleActionsUsagesCollector.PASTE_MAVEN_DEPENDENCY)
      val isKotlinDsl: Boolean = isKotlinBuildScriptFile(file.getName())
      return toGradleDependency(text, isKotlinDsl)
    }
    return text
  }

  private fun isApplicable(file: PsiFile): Boolean {
    return file.getName().endsWith('.'.toString() + GradleConstants.EXTENSION) || isKotlinBuildScriptFile(file.getName())
  }

  override fun requiresAllDocumentsToBeCommitted(editor: Editor, project: Project): Boolean {
    return false
  }

  companion object {
    private fun isKotlinBuildScriptFile(filename: String): Boolean {
      return filename.endsWith('.'.toString() + GradleConstants.KOTLIN_DSL_SCRIPT_EXTENSION)
    }

    private fun formatGradleDependency(
      groupId: String,
      artifactId: String,
      version: String,
      scope: String,
      classifier: String,
      isKotlinDsl: Boolean
    ): String {
      val gradleClassifier = if (classifier.isEmpty()) "" else ":" + classifier
      val gradleVersion = if (version.isEmpty()) "" else ":" + version
      val dependency = StringBuilder()
        .append(scope)
        .append(if (isKotlinDsl) "(\"" else " '")
        .append(groupId).append(':').append(artifactId)
        .append(gradleVersion)
        .append(gradleClassifier)
        .append(if (isKotlinDsl) "\")" else "'")

      return dependency.toString()
    }

    @JvmStatic
    @ApiStatus.Internal
    fun toGradleDependency(mavenDependency: String, isKotlinDsl: Boolean): String {
      try {
        val builder = createDocumentBuilder()
        val document = builder.parse(InputSource(StringReader(mavenDependency)))
        val gradleDependency: String? = extractGradleDependency(document, isKotlinDsl)
        return if (gradleDependency != null) gradleDependency else mavenDependency
      }
      catch (ignored: SAXException) {
      }
      catch (ignored: IOException) {
      }

      return mavenDependency
    }

    private fun extractGradleDependency(document: Document, isKotlinDsl: Boolean): String? {
      val groupId: String = getGroupId(document)
      val artifactId: String = getArtifactId(document)
      val version: String = getVersion(document)
      val scope: String = getScope(document)
      val classifier: String = getClassifier(document)

      if (groupId.isEmpty() || artifactId.isEmpty()) {
        return null
      }
      return formatGradleDependency(groupId, artifactId, version, scope, classifier, isKotlinDsl)
    }

    private fun getScope(document: Document): String {
      val scope: String = firstOrEmpty(document.getElementsByTagName("scope"))
      return when (scope) {
        "test" -> "testImplementation"
        "provided" -> "compileOnly"
        "runtime" -> "runtime"
        "compile" -> "implementation"
        else -> "implementation"
      }
    }

    private fun getVersion(document: Document): String {
      return firstOrEmpty(document.getElementsByTagName("version"))
    }

    private fun getArtifactId(document: Document): String {
      return firstOrEmpty(document.getElementsByTagName("artifactId"))
    }

    private fun getGroupId(document: Document): String {
      return firstOrEmpty(document.getElementsByTagName("groupId"))
    }

    private fun getClassifier(document: Document): String {
      return firstOrEmpty(document.getElementsByTagName("classifier"))
    }

    private fun firstOrEmpty(list: NodeList): String {
      val first = list.item(0)
      return if (first != null) first.getTextContent() else ""
    }

    private fun isMvnDependency(text: String): Boolean {
      val trimmed: String = trimLeadingComment(text.trim { it <= ' ' })
      if (trimmed.startsWith("<dependency>") && trimmed.endsWith("</dependency>")) {
        return true
      }
      return false
    }

    /**
     * Removes leading comment, usually it exists if dependency was copied from maven central site
     */
    private fun trimLeadingComment(text: String): String {
      val start = text.indexOf("<!--")
      val end = text.indexOf("-->")
      if (start == 0 && end > 0) {
        return text.substring(end + "-->".length).trim { it <= ' ' }
      }
      else {
        return text
      }
    }
  }
}
