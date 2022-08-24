package org.jetbrains.completion.full.line.platform

import org.jetbrains.completion.full.line.ProposalTransformer

val IncompleteWordTransformer = ProposalTransformer { proposal ->
  if (proposal.suggestion.endsWith("_")) {
    proposal.withSuggestion(proposal.suggestion.dropLastWhile { it.isJavaIdentifierPart() })
  }
  else {
    proposal
  }
}

val TrimTransformer = ProposalTransformer { proposal ->
  if (proposal.suggestion.isNotEmpty()
      && (proposal.suggestion.last().isWhitespace() || proposal.suggestion.first().isWhitespace())
  ) {
    proposal.withSuggestion(proposal.suggestion.trim())
  }
  else {
    proposal
  }
}
