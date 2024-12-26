// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.codeInsight.daemon.impl.quickfix.MoveClassToSeparateFileFix;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.convertToJava.git.RenameTrackingKt;

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

  @Override
  protected @NotNull UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new UsageViewDescriptorAdapter() {
      @Override
      public PsiElement @NotNull [] getElements() {
        return myFiles;
      }

      @Override
      public String getProcessedElementsHeader() {
        return GroovyRefactoringBundle.message("files.to.be.converted");
      }
    };
  }

  @Override
  protected UsageInfo @NotNull [] findUsages() {
    return UsageInfo.EMPTY_ARRAY;
  }

  //private static String
  @Override
  protected void performRefactoring(UsageInfo @NotNull [] usages) {
    final GeneratorClassNameProvider classNameProvider = new GeneratorClassNameProvider();

    ExpressionContext context = new ExpressionContext(myProject, myFiles);
    final ClassGenerator classGenerator = new ClassGenerator(classNameProvider, new ClassItemGeneratorImpl(context));

    for (GroovyFile file : myFiles) {
      final PsiClass[] classes = file.getClasses();
      StringBuilder builder = new StringBuilder();
      boolean first = true;
      for (PsiClass aClass : classes) {
        if (first) {
          int offset = aClass.getTextOffset();
          for (PsiElement child : file.getChildren()) {
            if (child.getTextOffset() >= offset) break;
            if (child instanceof PsiComment) {
              if (child instanceof GrDocComment docComment) {
                if (docComment.getOwner() != null) break;
              }
              builder.append(child.getText()).append('\n'); // keep copyright comments
            }
          }
        }
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
        String filePathBeforeConvert = file.getVirtualFile().getPath();
        file.getVirtualFile().putUserData(RenameTrackingKt.getPathBeforeGroovyToJavaConversion(), filePathBeforeConvert);

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
    PsiDocumentManager.getInstance(myProject).doPostponedOperationsAndUnblockDocument(newFile.getContainingFile().getFileDocument());
    PsiClass[] inner = ((PsiJavaFile)newFile).getClasses();
    for (PsiClass psiClass : inner) {
      var fix = new MoveClassToSeparateFileFix(psiClass).asIntention();
      if (fix.isAvailable(myProject, null, (PsiFile)newFile)) {
        fix.invoke(myProject, null, (PsiFile)newFile);
      }
    }
  }

  private static String getNewFileName(GroovyFile file) {
    final PsiDirectory dir = file.getContainingDirectory();
    LOG.assertTrue(dir != null);


    final PsiFile[] files = dir.getFiles();
    Set<String> fileNames = ContainerUtil.map2Set(files, PsiFileSystemItem::getName);
    String prefix = FileUtilRt.getNameWithoutExtension(file.getName());
    return UniqueNameGenerator.generateUniqueName(prefix, "", ".java", fileNames);
  }

  @Override
  protected @NotNull String getCommandName() {
    return GroovyRefactoringBundle.message("converting.files.to.java");
  }
}
