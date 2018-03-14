/*
 * @author max
 */
package com.intellij.util.pbxproj;

import com.intellij.lang.Language;

public class PbxLanguage extends Language {
  public static final PbxLanguage INSTANCE = new PbxLanguage();

  private PbxLanguage() {
    super("pbx");
  }
}
