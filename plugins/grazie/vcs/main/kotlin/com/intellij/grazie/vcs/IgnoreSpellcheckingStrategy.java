package com.intellij.grazie.vcs;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;

final class IgnoreSpellcheckingStrategy extends SpellcheckingStrategy implements DumbAware {
  @Override
  public boolean useTextLevelSpellchecking() {
    return Registry.is("spellchecker.grazie.enabled", false);
  }
}
