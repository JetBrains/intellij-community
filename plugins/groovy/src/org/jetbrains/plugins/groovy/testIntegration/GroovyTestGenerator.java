// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.testIntegration;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.testIntegration.TestFramework;
import com.intellij.testIntegration.TestIntegrationUtils;
import com.intellij.testIntegration.createTest.CreateTestDialog;
import com.intellij.testIntegration.createTest.TestGenerator;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.actions.GroovyTemplates;
import org.jetbrains.plugins.groovy.annotator.intentions.CreateClassActionBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Maxim.Medvedev
 */
public class GroovyTestGenerator implements TestGenerator {

  @Nullable
  @Override
  public PsiElement generateTest(final Project project, final CreateTestDialog d) {
    return WriteAction.compute(() -> {
      final PsiClass test = (PsiClass)PostprocessReformattingAspect.getInstance(project).postponeFormattingInside(
        (Computable<PsiElement>)() -> {
          try {
            IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace();

            GrTypeDefinition targetClass = CreateClassActionBase.createClassByType(
              d.getTargetDirectory(),
              d.getClassName(),
              PsiManager.getInstance(project),
              null,
              GroovyTemplates.GROOVY_CLASS, true);
            if (targetClass == null) return null;

            addSuperClass(targetClass, project, d.getSuperClassName());

            Editor editor = CodeInsightUtil.positionCursorAtLBrace(project, targetClass.getContainingFile(), targetClass);
            addTestMethods(editor,
                           targetClass,
                           d.getSelectedTestFrameworkDescriptor(),
                           d.getSelectedMethods(),
                           d.shouldGeneratedBefore(),
                           d.shouldGeneratedAfter());
            return targetClass;
          }
          catch (IncorrectOperationException e1) {
            showErrorLater(project, d.getClassName());
            return null;
          }
        });
      if (test == null) return null;
      JavaCodeStyleManager.getInstance(test.getProject()).shortenClassReferences(test);
      CodeStyleManager.getInstance(project).reformat(test);
      return test;
    });
  }

  @Override
  public String toString() {
    return GroovyBundle.message("language.groovy");
  }

  private static void addSuperClass(@NotNull GrTypeDefinition targetClass, @NotNull Project project, @Nullable String superClassName)
    throws IncorrectOperationException {
    if (superClassName == null) return;

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

    PsiClass superClass = findClass(project, superClassName);
    GrCodeReferenceElement superClassRef;
    if (superClass != null) {
      superClassRef = factory.createCodeReferenceElementFromClass(superClass);
    }
    else {
      superClassRef = factory.createCodeReference(superClassName);
    }
    GrExtendsClause extendsClause = targetClass.getExtendsClause();
    if (extendsClause == null) {
      extendsClause = (GrExtendsClause)targetClass.addAfter(factory.createExtendsClause(), targetClass.getNameIdentifierGroovy());
    }

    extendsClause.add(superClassRef);
  }

  @Nullable
  private static PsiClass findClass(Project project, String fqName) {
    GlobalSearchScope scope = GlobalSearchScope.allScope(project);
    return JavaPsiFacade.getInstance(project).findClass(fqName, scope);
  }

  private static void addTestMethods(Editor editor,
                                     PsiClass targetClass,
                                     TestFramework descriptor,
                                     Collection<? extends MemberInfo> methods,
                                     boolean generateBefore,
                                     boolean generateAfter) throws IncorrectOperationException {
    final HashSet<String> existingNames = new HashSet<>();
    if (generateBefore) {
      generateMethod(TestIntegrationUtils.MethodKind.SET_UP, descriptor, targetClass, editor, null, existingNames);
    }
    if (generateAfter) {
      generateMethod(TestIntegrationUtils.MethodKind.TEAR_DOWN, descriptor, targetClass, editor, null, existingNames);
    }
    for (MemberInfo m : methods) {
      generateMethod(TestIntegrationUtils.MethodKind.TEST, descriptor, targetClass, editor, m.getMember().getName(), existingNames);
    }
  }

  private static void showErrorLater(final Project project, final String targetClassName) {
    ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(project,
                                                                               JavaBundle.message("intention.error.cannot.create.class.message", targetClassName),
                                                                               JavaBundle.message("intention.error.cannot.create.class.title")));
  }

  private static void generateMethod(@NotNull TestIntegrationUtils.MethodKind methodKind,
                                     TestFramework descriptor,
                                     PsiClass targetClass,
                                     Editor editor,
                                     @Nullable String name, Set<? super String> existingNames) {
    GroovyPsiElementFactory f = GroovyPsiElementFactory.getInstance(targetClass.getProject());
    PsiMethod method = (PsiMethod)targetClass.add(f.createMethod("dummy", PsiTypes.voidType()));
    PsiDocumentManager.getInstance(targetClass.getProject()).doPostponedOperationsAndUnblockDocument(editor.getDocument());
    TestIntegrationUtils.runTestMethodTemplate(methodKind, descriptor, editor, targetClass, method, name, true, existingNames);
  }
}
