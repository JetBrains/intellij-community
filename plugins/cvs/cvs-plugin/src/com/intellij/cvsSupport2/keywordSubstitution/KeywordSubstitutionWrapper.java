// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.keywordSubstitution;

import com.intellij.CvsBundle;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * author: lesya
 */
public final class KeywordSubstitutionWrapper {

  private final KeywordSubstitution myKeywordSubstitution;
  private final String myDisplayName;

  public static final KeywordSubstitutionWrapper KEYWORD_EXPANSION = new KeywordSubstitutionWrapper(KeywordSubstitution.KEYWORD_EXPANSION, CvsBundle.message("keyword.substitution.expansion"));
  public static final KeywordSubstitutionWrapper KEYWORD_EXPANSION_LOCKER = new KeywordSubstitutionWrapper(KeywordSubstitution.KEYWORD_EXPANSION_LOCKER, CvsBundle.message("keyword.substitution.expansion.locker"));
  public static final KeywordSubstitutionWrapper KEYWORD_COMPRESSION = new KeywordSubstitutionWrapper(KeywordSubstitution.KEYWORD_COMPRESSION, CvsBundle.message("keyword.substitution.compression"));
  public static final KeywordSubstitutionWrapper NO_SUBSTITUTION = new KeywordSubstitutionWrapper(KeywordSubstitution.NO_SUBSTITUTION, CvsBundle.message("keyword.substitution.no.substitution"));
  public static final KeywordSubstitutionWrapper BINARY = new KeywordSubstitutionWrapper(KeywordSubstitution.BINARY, CvsBundle.message("keyword.substitution.binary"));
  public static final KeywordSubstitutionWrapper KEYWORD_REPLACEMENT = new KeywordSubstitutionWrapper(KeywordSubstitution.KEYWORD_REPLACEMENT, CvsBundle.message("keyword.substitution.replacement"));

  private static List<KeywordSubstitutionWrapper> values = null;

  private KeywordSubstitutionWrapper(KeywordSubstitution keywordSubstitution, String displayName) {
    myKeywordSubstitution = keywordSubstitution;
    myDisplayName = displayName;
  }

  public KeywordSubstitution getSubstitution() {
    return myKeywordSubstitution;
  }

  public String toString() {
    return myDisplayName;
  }

  public static KeywordSubstitutionWrapper getValue(String substitution) {
    final KeywordSubstitution keywordSubstitution = KeywordSubstitution.getValue(substitution);
    return getValue(keywordSubstitution);
  }

  public static KeywordSubstitutionWrapper getValue(KeywordSubstitution substitution) {
    if (substitution == KeywordSubstitution.BINARY) return BINARY;
    if (substitution == KeywordSubstitution.KEYWORD_COMPRESSION) return KEYWORD_COMPRESSION;
    if (substitution == KeywordSubstitution.KEYWORD_EXPANSION) return KEYWORD_EXPANSION;
    if (substitution == KeywordSubstitution.KEYWORD_EXPANSION_LOCKER) return KEYWORD_EXPANSION_LOCKER;
    if (substitution == KeywordSubstitution.NO_SUBSTITUTION) return NO_SUBSTITUTION;
    if (substitution == KeywordSubstitution.KEYWORD_REPLACEMENT) return KEYWORD_REPLACEMENT;
    return null;
  }

  public static void fillComboBox(JComboBox comboBox, KeywordSubstitution defaultSubstitution) {
    for (KeywordSubstitutionWrapper value : values()) {
      comboBox.addItem(value);
    }
    if (defaultSubstitution != null) {
      comboBox.setSelectedItem(getValue(defaultSubstitution));
    }
  }

  public static List<KeywordSubstitutionWrapper> values() {
    if (values == null) {
      final ArrayList<KeywordSubstitutionWrapper> list = new ArrayList();
      list.add(KEYWORD_EXPANSION);
      list.add(KEYWORD_EXPANSION_LOCKER);
      list.add(KEYWORD_COMPRESSION);
      list.add(NO_SUBSTITUTION);
      list.add(BINARY);
      list.add(KEYWORD_REPLACEMENT);
      values = Collections.unmodifiableList(list);
    }
    return values;
  }
}
