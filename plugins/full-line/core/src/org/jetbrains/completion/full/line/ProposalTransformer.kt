package org.jetbrains.completion.full.line

import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.completion.full.line.language.FullLineLanguageSupporter

// Transforms FL proposals at very first step of pipeline
fun interface ProposalTransformer {
  fun transform(proposal: RawFullLineProposal): RawFullLineProposal

  companion object {
    fun identity(): ProposalTransformer {
      return ProposalTransformer { proposal -> proposal }
    }

    fun firstToken(supporter: FullLineLanguageSupporter): ProposalTransformer {
      return ProposalTransformer {
        val firstToken = supporter.getFirstToken(it.suggestion)
        if (firstToken != null) it.withSuggestion(firstToken) else it
      }
    }

    fun reformatCode(
      supporter: FullLineLanguageSupporter,
      file: PsiFile,
      offset: Int,
      prefix: String
    ): ProposalTransformer = ProposalTransformer { proposal ->
      val fileText = file.text
      val beforeSuggestion = fileText
        .take(offset - prefix.length)
        .filterNot { it.isWhitespace() }
        .length
      val psi = supporter.createCodeFragment(file, fileText.take(offset - prefix.length) + proposal.suggestion, false)
                ?: return@ProposalTransformer proposal
      var currentNotWhitespaces = 0
      val formattedSuggestion = CodeStyleManager.getInstance(file.project)
        .reformat(psi, true).text
        .dropWhile {
          if (!it.isWhitespace()) {
            currentNotWhitespaces++
          }
          currentNotWhitespaces <= beforeSuggestion
        }

      proposal.withSuggestion(formattedSuggestion)
    }
  }
}
