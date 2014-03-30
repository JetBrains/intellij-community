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
package org.jetbrains.plugins.groovy.lang.formatter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.codeStyle.GroovyCodeStyleSettings
/**
 * @author peter
 */
public abstract class GroovyFormatterTestCase extends LightCodeInsightFixtureTestCase {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.lang.formatter.GroovyFormatterTestCase");
  protected CodeStyleSettings myTempSettings;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setSettings(getProject());

    groovySettings.CLASS_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    groovySettings.METHOD_BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
    groovySettings.BRACE_STYLE = CommonCodeStyleSettings.END_OF_LINE;
  }

  @Override
  protected void tearDown() throws Exception {
    setSettingsBack();
    super.tearDown();
  }
  
  protected CommonCodeStyleSettings getGroovySettings() {
    return myTempSettings.getCommonSettings(GroovyFileType.GROOVY_LANGUAGE);
  }

  protected GroovyCodeStyleSettings getGroovyCustomSettings() {
    return myTempSettings.getCustomSettings(GroovyCodeStyleSettings)
  }

  protected void setSettings(Project project) {
    assertNull(myTempSettings);
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);
    myTempSettings = settings.clone();

    CommonCodeStyleSettings.IndentOptions gr = myTempSettings.getIndentOptions(GroovyFileType.GROOVY_FILE_TYPE);
    assertNotSame(gr, settings.OTHER_INDENT_OPTIONS);
    gr.INDENT_SIZE = 2;
    gr.CONTINUATION_INDENT_SIZE = 4;
    gr.TAB_SIZE = 2;
    myTempSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 3;

    CodeStyleSettingsManager.getInstance(project).setTemporarySettings(myTempSettings);
  }

  protected void setSettingsBack() {
    final CodeStyleSettingsManager manager = CodeStyleSettingsManager.getInstance(getProject());
    myTempSettings.getIndentOptions(GroovyFileType.GROOVY_FILE_TYPE).INDENT_SIZE = 200;
    myTempSettings.getIndentOptions(GroovyFileType.GROOVY_FILE_TYPE).CONTINUATION_INDENT_SIZE = 200;
    myTempSettings.getIndentOptions(GroovyFileType.GROOVY_FILE_TYPE).TAB_SIZE = 200;

    myTempSettings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = 5;
    manager.dropTemporarySettings();
    myTempSettings = null;
  }

  protected void checkFormatting(String fileText, String expected) {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText);
    checkFormatting(expected);
  }

  protected void doFormat(final PsiFile file) {
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            try {
              TextRange myTextRange = file.getTextRange();
              CodeStyleManager.getInstance(file.getProject()).reformatText(file, myTextRange.getStartOffset(), myTextRange.getEndOffset());
            } catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        });
      }
    }, null, null);
  }

  protected void checkFormatting(String expected) {
    doFormat(myFixture.getFile());
    myFixture.checkResult(expected);
  }
}
