package training.featuresSuggester.actions

import com.intellij.lang.Language
import com.intellij.openapi.project.Project

@Suppress("UnnecessaryAbstractClass")
abstract class Action {
  abstract val timeMillis: Long
  abstract val language: Language?
  abstract val project: Project?
}
