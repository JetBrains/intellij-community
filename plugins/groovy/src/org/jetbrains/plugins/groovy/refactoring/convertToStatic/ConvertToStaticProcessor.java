/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.convertToStatic;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_TRANSFORM_COMPILE_STATIC;

public class ConvertToStaticProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(ConvertToStaticProcessor.class);

  private final GroovyFile[] myFiles;

  protected ConvertToStaticProcessor(Project project, GroovyFile... files) {
    super(project);
    myFiles = files;
  }

  @NotNull
  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new UsageViewDescriptorAdapter() {
      @NotNull
      @Override
      public PsiElement[] getElements() {
        return myFiles;
      }

      @Override
      public String getProcessedElementsHeader() {
        return GroovyRefactoringBundle.message("files.to.be.converted");
      }
    };
  }

  @NotNull
  @Override
  protected UsageInfo[] findUsages() {
    return UsageInfo.EMPTY_ARRAY;
  }

  @Override
  protected void performRefactoring(@NotNull UsageInfo[] usages) {

    for (GroovyFile file : myFiles) {
      TypeChecker checker = new TypeChecker();
      final Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);
      PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(file.getProject());
      LOG.assertTrue(document != null);
      psiDocumentManager.commitDocument(document);
      LOG.assertTrue(file.isValid());
      if (file.isScript()) continue;
      PsiClass[] classes = file.getClasses();

      for (PsiClass psiClass : classes) {
        if (PsiImplUtil.getAnnotation(psiClass, GROOVY_TRANSFORM_COMPILE_STATIC) == null) {
          addAnnotation(psiClass);
        }
        checkErrors((GrTypeDefinition)psiClass, checker);
      }
      checker.applyFixes();

      PsiDocumentManager.getInstance(myProject).commitDocument(document);

      doPostProcessing(file);
    }
  }

  private static void checkErrors(@NotNull GrTypeDefinition psiClass, @NotNull TypeChecker checker) {
    psiClass.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        element.accept(new GroovyPsiElementVisitor(checker));

        super.visitElement(element);
      }
    });
  }

  @NotNull
  @Override
  protected String getCommandName() {
    return GroovyRefactoringBundle.message("converting.files.to.static");
  }

  void addAnnotation(@NotNull PsiClass psiClass) {
    PsiModifierList modifierList = psiClass.getModifierList();
    if (modifierList != null) {
      modifierList.addAnnotation(GROOVY_TRANSFORM_COMPILE_STATIC);
    }
  }

  private void doPostProcessing(PsiElement newFile) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    newFile = JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(newFile);
    CodeStyleManager.getInstance(myProject).reformat(newFile);
  }
}
