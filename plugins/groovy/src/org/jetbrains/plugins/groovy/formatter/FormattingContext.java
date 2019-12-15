// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.formatter;

import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings;

/**
 * @author Max Medvedev
 */
public class FormattingContext {
  private final CommonCodeStyleSettings mySettings;
  private final GroovyCodeStyleSettings myGroovySettings;
  private final AlignmentProvider myAlignmentProvider;

  private final boolean myForbidWrapping;

  public FormattingContext(@NotNull CommonCodeStyleSettings settings,
                           @NotNull AlignmentProvider provider,
                           @NotNull GroovyCodeStyleSettings groovySettings, boolean forbidWrapping) {
    mySettings = settings;
    myAlignmentProvider = provider;
    myGroovySettings = groovySettings;
    this.myForbidWrapping = forbidWrapping;
  }

  public CommonCodeStyleSettings getSettings() {
    return mySettings;
  }

  public AlignmentProvider getAlignmentProvider() {
    return myAlignmentProvider;
  }

  public GroovyCodeStyleSettings getGroovySettings() {
    return myGroovySettings;
  }

  public FormattingContext createContext(boolean forbidWrapping) {
    return new FormattingContext(mySettings, myAlignmentProvider, myGroovySettings, myForbidWrapping || forbidWrapping);
  }

  public boolean isForbidWrapping() {
    return myForbidWrapping;
  }
}
