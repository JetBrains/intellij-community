/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  private final boolean myInsidePlainGString;

  public FormattingContext(@NotNull CommonCodeStyleSettings settings,
                           @NotNull AlignmentProvider provider,
                           @NotNull GroovyCodeStyleSettings groovySettings, boolean insidePlainGString) {
    mySettings = settings;
    myAlignmentProvider = provider;
    myGroovySettings = groovySettings;
    myInsidePlainGString = insidePlainGString;
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

  public FormattingContext createContext(boolean insidePlainGString) {
    return new FormattingContext(mySettings, myAlignmentProvider, myGroovySettings, insidePlainGString);
  }

  public boolean isInsidePlainGString() {
    return myInsidePlainGString;
  }
}
