/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.codeInsight.daemon.impl.quickfix.MoveClassToSeparateFileFix;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;

import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class ConvertToJavaProcessor extends BaseRefactoringProcessor {
  private static final Logger LOG = Logger.getInstance(ConvertToJavaProcessor.class);

  private final GroovyFile[] myFiles;

  protected ConvertToJavaProcessor(Project project, GroovyFile... files) {
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

  //private static String
  @Override
  protected void performRefactoring(@NotNull UsageInfo[] usages) {
    final GeneratorClassNameProvider classNameProvider = new GeneratorClassNameProvider();

    ExpressionContext context = new ExpressionContext(myProject, myFiles);
    final ClassGenerator classGenerator = new ClassGenerator(classNameProvider, new ClassItemGeneratorImpl(context));

    for (GroovyFile file : myFiles) {
      final PsiClass[] classes = file.getClasses();
      StringBuilder builder = new StringBuilder();
      boolean first = true;
      for (PsiClass aClass : classes) {
        classGenerator.writeTypeDefinition(builder, aClass, true, first);
        first = false;
        builder.append('\n');
      }

      final Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);
      LOG.assertTrue(document != null);
      document.setText(builder.toString());
      PsiDocumentManager.getInstance(myProject).commitDocument(document);
      String fileName = getNewFileName(file);
      PsiElement newFile;
      try {
        newFile = file.setName(fileName);
      }
      catch (final IncorrectOperationException e) {
        ApplicationManager.getApplication().invokeLater(
          () -> Messages.showMessageDialog(myProject, e.getMessage(), RefactoringBundle.message("error.title"), Messages.getErrorIcon()));
        return;
      }

      doPostProcessing(newFile);
    }
  }

  private void doPostProcessing(PsiElement newFile) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    // don't move classes to new files with corresponding class names and reformat

    if (!(newFile instanceof PsiJavaFile)) {
      LOG.info(".java is not assigned to java file type");
      return;
    }

    newFile = JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(newFile);
    newFile = CodeStyleManager.getInstance(myProject).reformat(newFile);
    PsiClass[] inner = ((PsiJavaFile)newFile).getClasses();
    for (PsiClass psiClass : inner) {
      MoveClassToSeparateFileFix fix = new MoveClassToSeparateFileFix(psiClass);
      if (fix.isAvailable(myProject, null, (PsiFile)newFile)) {
        fix.invoke(myProject, null, (PsiFile)newFile);
      }
    }
  }

  private static String getNewFileName(GroovyFile file) {
    final PsiDirectory dir = file.getContainingDirectory();
    LOG.assertTrue(dir != null);


    final PsiFile[] files = dir.getFiles();
    Set<String> fileNames = new HashSet<>();
    for (PsiFile psiFile : files) {
      fileNames.add(psiFile.getName());
    }
    String prefix = FileUtil.getNameWithoutExtension(file.getName());
    String fileName = prefix + ".java";
    int index = 1;
    while (fileNames.contains(fileName)) {
      fileName = prefix + index + ".java";
    }
    return fileName;
  }

  @Override
  protected String getCommandName() {
    return GroovyRefactoringBundle.message("converting.files.to.java");
  }
}
