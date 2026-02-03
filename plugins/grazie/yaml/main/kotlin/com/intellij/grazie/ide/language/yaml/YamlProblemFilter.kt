package com.intellij.grazie.ide.language.yaml

import com.intellij.grazie.text.ProblemFilter
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextProblem
import org.jetbrains.yaml.YAMLTokenTypes.SCALAR_KEY

internal class YamlProblemFilter : ProblemFilter() {
  override fun shouldIgnore(problem: TextProblem): Boolean {
    if (problem.text.domain == TextContent.TextDomain.LITERALS) {
      val root = problem.text.commonParent
      return root.node != null && root.node.elementType == SCALAR_KEY
    }
    return false
  }
}