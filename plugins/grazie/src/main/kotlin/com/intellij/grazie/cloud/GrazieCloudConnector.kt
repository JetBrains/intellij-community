package com.intellij.grazie.cloud

import ai.grazie.ner.model.SentenceWithNERAnnotations
import ai.grazie.nlp.langs.Language
import ai.grazie.tree.model.SentenceWithTreeDependencies
import com.intellij.grazie.GrazieConfig.State.Processing
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface GrazieCloudConnector {

  /**
   * Returns true if there is a connection to Grazie Cloud and [connectionType] is [Processing.Cloud]
   */
  fun seemsCloudConnected(): Boolean

  /**
   * Returns the type of the connection. Usually set in settings: ([Processing.Local] or [Processing.Cloud]).
   */
  fun connectionType(): Processing

  /**
   * Returns true if there was a recent error during the last GEC request.
   */
  fun isAfterRecentGecError(): Boolean

  /**
   * Rephrases the given [text] at the given [ranges] in the given [language].
   */
  fun rephrase(text: String, ranges: List<TextRange>, language: Language, project: Project): List<String>?

  /**
   * Marks [sentences] with Named Entity Recognition Annotations for the given [language].
   */
  suspend fun nerAnnotations(language: Language, sentences: List<String>, project: Project): List<SentenceWithNERAnnotations>?

  /**
   * Returns syntactic dependency trees for the given [sentences] using specified language model and parser options.
   */
  suspend fun trees(language: Language, modelName: String, parserOptions: List<String>, sentences: List<String>, project: Project): List<SentenceWithTreeDependencies>?

  /**
   * Subscribe to authorization state change events.
   */
  fun subscribeToAuthorizationStateEvents(disposable: Disposable, listener: () -> Unit)

  companion object {
    val EP_NAME = ExtensionPointName<GrazieCloudConnector>("com.intellij.grazie.cloudConnector")

    fun seemsCloudConnected(): Boolean = EP_NAME.extensionList.any { it.seemsCloudConnected() }

    fun subscribeToAuthorizationStateEvents(disposable: Disposable, listener: () -> Unit): Unit =
      EP_NAME.forEachExtensionSafe { it.subscribeToAuthorizationStateEvents(disposable, listener) }
  }
}