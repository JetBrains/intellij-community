package org.jetbrains.android.refactoring;

import org.jetbrains.annotations.TestOnly;

/**
* @author Eugene.Kudelevsky
*/
class AndroidInlineTestConfig {
  private final boolean myInlineThisOnly;

  @TestOnly
  AndroidInlineTestConfig(boolean inlineThisOnly) {
    myInlineThisOnly = inlineThisOnly;
  }

  public boolean isInlineThisOnly() {
    return myInlineThisOnly;
  }
}
