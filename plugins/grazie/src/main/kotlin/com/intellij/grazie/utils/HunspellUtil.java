package com.intellij.grazie.utils;

import ai.grazie.nlp.langs.Language;
import ai.grazie.spell.lists.hunspell.HunspellWordList;
import com.intellij.grazie.jlanguage.Lang;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class HunspellUtil {

  public static @NotNull HunspellWordList obtainEnglish() {
    return Objects.requireNonNull(obtainDictionary(Language.ENGLISH));
  }

  public static @Nullable HunspellWordList obtainDictionary(Language language) {
    Lang lang = HighlightingUtil.findInstalledLang(language);
    if (lang != null && lang.getDictionary() != null) {
      return lang.getDictionary().getDict();
    }
    return null;
  }
}
