/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.testIntegration;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testIntegration.createTest.CreateTestDialog;
import com.intellij.testIntegration.createTest.JavaTestGenerator;
import com.intellij.testIntegration.createTest.TestGenerator;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.codeInspection.local.GroovyUnusedImportPass;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author Maxim.Medvedev
 */
public class GroovyTestGenerator implements TestGenerator {

  @Nullable
  @Override
  public PsiElement generateTest(final Project project, final CreateTestDialog d) {
    final PsiClass test = (PsiClass)new JavaTestGenerator().generateTest(project, d);
    if (test == null) return null;
    return ApplicationManager.getApplication().runWriteAction(new Computable<PsiElement>() {
      @Override
      public PsiElement compute() {
        final PsiFile file = test.getContainingFile();
        final String name = file.getName();
        final String newName = FileUtil.getNameWithoutExtension(name) + "." + GroovyFileType.DEFAULT_EXTENSION;

        final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
        try {
          final PsiElement element = file.setName(newName);

          final GroovyFile newFile = (GroovyFile)codeStyleManager.reformat(element);
          GroovyUnusedImportPass.optimizeImports(project, newFile);
          return newFile.getClasses()[0];
        }
        catch (IncorrectOperationException e) {
          file.delete();
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              Messages.showErrorDialog(project,
                                       CodeInsightBundle.message("intention.error.cannot.create.class.message", d.getClassName()),
                                       CodeInsightBundle.message("intention.error.cannot.create.class.title"));
            }
          });
          return null;
        }
      }
    });
  }

  @Override
  public String toString() {
    return GroovyIntentionsBundle.message("intention.crete.test.groovy");
  }
}
