/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.jetbrains.plugins.groovy.lang.formatter;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import junit.framework.Test;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.testcases.simple.SimpleGroovyFileSetTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * Test suite for static formatting. Compares two files:
 * befor and after formatting
 *
 * @author Ilya.Sergey
 */
public class FormatterTest extends SimpleGroovyFileSetTestCase {
  @NonNls
  private static final String DATA_PATH = "test/org/jetbrains/plugins/groovy/lang/formatter/data/actual";

  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.formatter.FormatterTest");

  public FormatterTest() {
    super(System.getProperty("path") != null ?
        System.getProperty("path") :
        DATA_PATH
    );
  }

  protected void performFormatting(final Project project, final PsiFile file) throws IncorrectOperationException {
    TextRange myTextRange = file.getTextRange();
    CodeStyleManager.getInstance(project).reformatText(file, myTextRange.getStartOffset(), myTextRange.getEndOffset());
  }

  public String transform(String testName, String[] data) throws Exception {
    String fileText = data[0];
    final PsiFile psiFile = TestUtils.createPseudoPhysicalFile(myProject, fileText);
    CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              performFormatting(myProject, psiFile);
            } catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        });
      }
    }, null, null);
    String text = psiFile.getText();
    System.out.println("------------------------ " + testName + " ------------------------");
    System.out.println(text);
    System.out.println("");
    return text;
  }


  public static Test suite() {
    return new FormatterTest();
  }

}
