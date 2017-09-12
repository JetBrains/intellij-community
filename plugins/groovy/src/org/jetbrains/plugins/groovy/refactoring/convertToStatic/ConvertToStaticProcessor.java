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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.annotator.InaccessibleElementVisitor;
import org.jetbrains.plugins.groovy.annotator.ResolveHighlightingVisitor;
import org.jetbrains.plugins.groovy.annotator.VisitorCallback;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringBundle;
import org.jetbrains.plugins.groovy.refactoring.convertToStatic.fixes.BaseFix;
import org.jetbrains.plugins.groovy.refactoring.convertToStatic.fixes.EmptyFieldTypeFix;
import org.jetbrains.plugins.groovy.refactoring.convertToStatic.fixes.EmptyReturnTypeFix;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_TRANSFORM_COMPILE_DYNAMIC;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_TRANSFORM_COMPILE_STATIC;

public class ConvertToStaticProcessor extends BaseRefactoringProcessor {
  private static final int maxIterations = 5;
  private static final Logger LOG = Logger.getInstance(ConvertToStaticProcessor.class);

  private final GroovyFile[] myFiles;

  private BaseFix[] myFixes = {new EmptyFieldTypeFix(), new EmptyReturnTypeFix()};

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
    Queue<GroovyFile> files = new ArrayDeque<>(Arrays.asList(myFiles));

    while (files.peek() != null) {
      GroovyFile file = files.poll();
      final Document document = PsiDocumentManager.getInstance(myProject).getDocument(file);
      PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(file.getProject());
      LOG.assertTrue(document != null);
      psiDocumentManager.commitDocument(document);
      LOG.assertTrue(file.isValid());
      if (file.isScript()) continue;

      try {
        applyFixes(file);
        putCompileAnnotations(file);
        checkErrors(file);
      } catch (Exception e) {
        LOG.error("Error in converting file: " + file.getName(), e);
      }


      PsiDocumentManager.getInstance(myProject).commitDocument(document);
      doPostProcessing(file);
    }
  }

  private void applyFixes(GroovyFile file) {
    file.accept(new PsiRecursiveElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        for (BaseFix fix : myFixes) element.accept(new GroovyPsiElementVisitor(fix));
        super.visitElement(element);
      }
    });
  }

  private void putCompileAnnotations(@NotNull GroovyFile file) {
    GrTypeDefinition[] classes = file.getTypeDefinitions();

    for (GrTypeDefinition typeDef : classes) { //put annotations
      addAnnotation(typeDef, true);
    }

    Set<GrTypeDefinition> classesWithUnresolvedRef = new HashSet<>();
    Set<GrMethod> methodsWithUnresolvedRef = new HashSet<>();

    VisitorCallback callback = (element, info) -> {
      GrMethod containingMethod = PsiTreeUtil.getParentOfType(element, GrMethod.class);
      if (containingMethod != null) {
        methodsWithUnresolvedRef.add(containingMethod);
        return;
      }
      GrTypeDefinition containingClass = PsiTreeUtil.getParentOfType(element, GrTypeDefinition.class);
      if (containingClass != null) classesWithUnresolvedRef.add(containingClass);
    };
    file.accept(new ResolveHighlightingVisitor(file, myProject, callback));
    file.accept(new InaccessibleElementVisitor(file, myProject, callback));

    for (GrTypeDefinition typeDef : classes) { //remove if found not compilable code in class def
      boolean isStaticClass = true;
      if (classesWithUnresolvedRef.contains(typeDef)) {
        isStaticClass = false;
        removeAnnotation(typeDef);
      }

      for (GrMethod method : typeDef.getCodeMethods()) {
        if (methodsWithUnresolvedRef.contains(method) == isStaticClass) {
          addAnnotation(method, !isStaticClass);
        }
      }
    }
  }

  private static void checkErrors(@NotNull GroovyFile file) {
    for (int iteration = 0; iteration < maxIterations; iteration++) {
      TypeChecker checker = new TypeChecker();
      file.accept(new PsiRecursiveElementVisitor() {
        @Override
        public void visitElement(PsiElement element) {
          if (PsiUtil.isCompileStatic(element)) {
            element.accept(new GroovyPsiElementVisitor(checker));
          }
          super.visitElement(element);
        }
      });
      if (checker.applyFixes() == 0) {
        return;
      }
    }
  }

  @NotNull
  @Override
  protected String getCommandName() {
    return GroovyRefactoringBundle.message("converting.files.to.static");
  }

  void addAnnotation(@NotNull PsiModifierListOwner owner, boolean isStatic) {
    PsiModifierList modifierList = owner.getModifierList();
    String annotation = isStatic ? GROOVY_TRANSFORM_COMPILE_STATIC : GROOVY_TRANSFORM_COMPILE_DYNAMIC;
    if (modifierList != null && modifierList.findAnnotation(annotation) == null) {
      modifierList.addAnnotation(annotation);
    }
  }

  void removeAnnotation(@NotNull PsiModifierListOwner owner) {
    PsiModifierList modifierList = owner.getModifierList();
    if (modifierList != null) {
      PsiAnnotation psiAnnotation = modifierList.findAnnotation(GROOVY_TRANSFORM_COMPILE_STATIC);
      if (psiAnnotation != null) psiAnnotation.delete();
    }
  }

  private void doPostProcessing(PsiElement newFile) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    newFile = JavaCodeStyleManager.getInstance(myProject).shortenClassReferences(newFile);
    CodeStyleManager.getInstance(myProject).reformat(newFile);
  }
}
