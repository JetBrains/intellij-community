package com.intellij.grazie.rule;

import ai.grazie.rules.RuleClient;

public class RuleIdeClient implements RuleClient {
  public static final RuleIdeClient INSTANCE = new RuleIdeClient();

  @Override
  public boolean supportsMetadataBasedDocumentAnalysis() {
    return true;
  }

  @Override
  public boolean supportsAutoFixes() {
    return true;
  }

  @Override
  public boolean hasImplicitVariant() {
    return true;
  }

  @Override
  public boolean hasVariantQuickFix() {
    return true;
  }

  @Override
  public boolean suggestDiacriticsByDefault() {
    return true;
  }

  @Override
  public boolean hasLocalMode() {
    return true;
  }
}
