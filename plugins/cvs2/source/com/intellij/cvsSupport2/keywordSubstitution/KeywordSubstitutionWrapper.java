package com.intellij.cvsSupport2.keywordSubstitution;

import org.netbeans.lib.cvsclient.command.KeywordSubstitution;

/**
 * author: lesya
 */
public class KeywordSubstitutionWrapper{

  private final KeywordSubstitution myKeywordSubstitution;
  private final String myDisplayName;

  public static final KeywordSubstitutionWrapper BINARY = new KeywordSubstitutionWrapper(KeywordSubstitution.BINARY, "binary");
  public static final KeywordSubstitutionWrapper KEYWORD_COMPRESSION = new KeywordSubstitutionWrapper(KeywordSubstitution.KEYWORD_COMPRESSION, "compression");
  public static final KeywordSubstitutionWrapper KEYWORD_EXPANSION = new KeywordSubstitutionWrapper(KeywordSubstitution.KEYWORD_EXPANSION, "expansion");
  public static final KeywordSubstitutionWrapper KEYWORD_EXPANSION_LOCKER = new KeywordSubstitutionWrapper(KeywordSubstitution.KEYWORD_EXPANSION_LOCKER, "expansion locker");
  public static final KeywordSubstitutionWrapper NO_SUBSTITUTION = new KeywordSubstitutionWrapper(KeywordSubstitution.NO_SUBSTITUTION, "no substitution");
  public static final KeywordSubstitutionWrapper KEYWORD_REPLACEMENT = new KeywordSubstitutionWrapper(KeywordSubstitution.KEYWORD_REPLACEMENT, "replacement");

  private KeywordSubstitutionWrapper(KeywordSubstitution keywordSubstitution, String displayName) {
    myKeywordSubstitution = keywordSubstitution;
    myDisplayName = displayName;
  }

  public KeywordSubstitution getSubstitution(){
    return myKeywordSubstitution;
  }

  public String toString() {
    return myDisplayName;
  }

  public static KeywordSubstitutionWrapper getValue(String substitution) {
    KeywordSubstitution keywordSubstitution = KeywordSubstitution.getValue(substitution);
    return getValue(keywordSubstitution);
  }

  public static KeywordSubstitutionWrapper getValue(KeywordSubstitution substitution) {
    if (substitution == KeywordSubstitution.BINARY) return KeywordSubstitutionWrapper.BINARY;
    if (substitution == KeywordSubstitution.KEYWORD_COMPRESSION) return KeywordSubstitutionWrapper.KEYWORD_COMPRESSION;
    if (substitution == KeywordSubstitution.KEYWORD_EXPANSION) return KeywordSubstitutionWrapper.KEYWORD_EXPANSION;
    if (substitution == KeywordSubstitution.KEYWORD_EXPANSION_LOCKER) return KeywordSubstitutionWrapper.KEYWORD_EXPANSION_LOCKER;
    if (substitution == KeywordSubstitution.NO_SUBSTITUTION) return KeywordSubstitutionWrapper.NO_SUBSTITUTION;
    if (substitution == KeywordSubstitution.KEYWORD_REPLACEMENT) return KeywordSubstitutionWrapper.KEYWORD_REPLACEMENT;
    return null;
  }

  public String getStringRepresentation(){
    if (myKeywordSubstitution == null) return null;
    return myKeywordSubstitution.toString();
  }
}
