package org.jetbrains.completion.full.line.platform

import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.ml.MLRankingIgnorable
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.completion.full.line.AnalyzedFullLineProposal
import org.jetbrains.completion.full.line.language.FullLineLanguageSupporter
import org.jetbrains.completion.full.line.platform.handlers.FullLineInsertHandler
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings
import java.math.RoundingMode
import java.text.DecimalFormat

class FullLineLookupElement(
  val head: String,
  val prefix: String,
  val proposal: AnalyzedFullLineProposal,
  private val supporter: FullLineLanguageSupporter,
  val selectedByTab: Boolean = false
) : LookupElement(), MLRankingIgnorable {
  val suffix
    get() = proposal.suffix

  private val settings = MLServerCompletionSettings.getInstance()

  override fun renderElement(presentation: LookupElementPresentation) {
    super.renderElement(presentation)

    presentation.apply {
      icon = supporter.iconSet.let { if (proposal.refCorrectness.isCorrect()) it.regular else it.redCode }
      typeText = typeText(proposal)
      tailText = proposal.suffix + '\t' + (when {
                                             selectedByTab -> TAIL_TEXT
                                             settings.showScore(supporter.language) -> " ${scoreFormatter.format(proposal.score * 100)}%"
                                             else -> null
                                           } ?: "")
    }

  }

  override fun getLookupString(): String {
    return proposal.suggestion
  }

  override fun handleInsert(context: InsertionContext) {
    FullLineInsertHandler.of(context, supporter).handleInsert(context, this)
  }

  private companion object {
    const val TYPE_TEXT = "full-line"
    const val TAIL_TEXT = " tab-selected"

    private val scoreFormatter = DecimalFormat("#.####").apply { roundingMode = RoundingMode.DOWN }

    private fun typeText(proposal: AnalyzedFullLineProposal): String {
      if (Registry.`is`("full.line.use.all.providers")) {
        return (proposal.provider?.let { "$it-" } ?: "") + TYPE_TEXT
      }

      return TYPE_TEXT
    }
  }
}
