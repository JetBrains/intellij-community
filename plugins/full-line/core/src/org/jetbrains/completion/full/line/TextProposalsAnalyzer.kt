package org.jetbrains.completion.full.line

// Adds additional information to raw proposals obtained from a model (PSI, correctness, etc)
interface TextProposalsAnalyzer {
  fun analyze(proposal: RawFullLineProposal): AnalyzedFullLineProposal
}
