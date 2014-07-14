package com.intellij.tokenindex;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class TokenIndexKey {
  private final Set<String> myLanguages;
  private final int myBlockId;

  public TokenIndexKey(@NotNull Set<String> languages, int blockId) {
    myLanguages = languages;
    myBlockId = blockId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    TokenIndexKey that = (TokenIndexKey)o;

    if (myBlockId != that.myBlockId) return false;
    if (!myLanguages.equals(that.myLanguages)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myLanguages.hashCode();
    result = 31 * result + myBlockId;
    return result;
  }

  @Override
  public String toString() {
    return myLanguages + ": " + myBlockId;
  }

  public Set<String> getLanguages() {
    return myLanguages;
  }

  public boolean containsLanguage(String languageId) {
    for (String language : myLanguages) {
      if (language.contains(languageId)) {
        return true;
      }
    }
    return false;
  }

  public int getBlockId() {
    return myBlockId;
  }
}
