/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.HighlightUtils;
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @see com.siyeh.ipp.fqnames.ReplaceFullyQualifiedNameWithImportIntention
 */
public class UnnecessaryFullyQualifiedNameInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean m_ignoreJavadoc = false; // left here to prevent changes to project files.

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final boolean inSameFile = ((Boolean)infos[0]).booleanValue();
    if (inSameFile) {
      return InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.problem.descriptor2");
    }
    return InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.problem.descriptor1");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryFullyQualifiedNameFix(((Boolean)infos[0]).booleanValue());
  }

  private static class UnnecessaryFullyQualifiedNameFix extends InspectionGadgetsFix {

    private final boolean inSameFile;

    public UnnecessaryFullyQualifiedNameFix(boolean inSameFile) {
      this.inSameFile = inSameFile;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace fully qualified name";
    }

    @Override
    @NotNull
    public String getName() {
      if (inSameFile) {
        return InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.remove.quickfix");
      }
      else {
        return InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.replace.quickfix");
      }
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)descriptor.getPsiElement();
      final PsiFile file = referenceElement.getContainingFile();
      final PsiElement target = referenceElement.resolve();
      if (!(target instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)target;
      ImportUtils.addImportIfNeeded(aClass, referenceElement);
      final String fullyQualifiedText = referenceElement.getText();
      final QualificationRemover qualificationRemover = new QualificationRemover(fullyQualifiedText);
      file.accept(qualificationRemover);
      if (isOnTheFly()) {
        final Collection<PsiElement> shortenedElements = qualificationRemover.getShortenedElements();
        HighlightUtils.highlightElements(shortenedElements);
        showStatusMessage(file.getProject(), shortenedElements.size());
      }
    }

    private static void showStatusMessage(Project project, int elementCount) {
      final WindowManager windowManager = WindowManager.getInstance();
      final StatusBar statusBar = windowManager.getStatusBar(project);
      if (statusBar == null) {
        return;
      }
      if (elementCount == 1) {
        statusBar.setInfo(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.status.bar.escape.highlighting.message1"));
      }
      else {
        statusBar.setInfo(InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.status.bar.escape.highlighting.message2",
          Integer.valueOf(elementCount - 1)));
      }
    }

    private static class QualificationRemover extends JavaRecursiveElementVisitor {

      private final String fullyQualifiedText;
      private final List<PsiElement> shortenedElements = new ArrayList();

      QualificationRemover(String fullyQualifiedText) {
        this.fullyQualifiedText = fullyQualifiedText;
      }

      public Collection<PsiElement> getShortenedElements() {
        return Collections.unmodifiableCollection(shortenedElements);
      }

      @Override
      public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
        super.visitReferenceElement(reference);
        final PsiElement parent = PsiTreeUtil.getParentOfType(reference, PsiImportStatementBase.class);
        if (parent != null) {
          return;
        }
        final String text = reference.getText();
        if (!text.equals(fullyQualifiedText)) {
          return;
        }
        final PsiElement qualifier = reference.getQualifier();
        if (qualifier == null) {
          return;
        }
        try {
          qualifier.delete();
        }
        catch (IncorrectOperationException e) {
          final Class<? extends QualificationRemover> aClass = getClass();
          final String className = aClass.getName();
          final Logger logger = Logger.getInstance(className);
          logger.error(e);
        }
        shortenedElements.add(reference);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryFullyQualifiedNameVisitor();
  }

  private static class UnnecessaryFullyQualifiedNameVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      checkReference(expression);
    }

    @Override
    public void visitReferenceElement(PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      checkReference(reference);
    }

    private void checkReference(PsiJavaCodeReferenceElement reference) {
      final PsiElement qualifier = reference.getQualifier();
      if (!(qualifier instanceof PsiJavaCodeReferenceElement)) {
        return;
      }
      final PsiElement parent = reference.getParent();
      if (parent instanceof PsiMethodCallExpression || parent instanceof PsiAssignmentExpression || parent instanceof PsiVariable) {
        return;
      }
      final PsiElement element =
        PsiTreeUtil.getParentOfType(reference, PsiImportStatementBase.class, PsiPackageStatement.class, JavaCodeFragment.class);
      if (element != null) {
        return;
      }
      final CodeStyleSettings styleSettings = CodeStyleSettingsManager.getSettings(reference.getProject());
      if (styleSettings.USE_FQ_CLASS_NAMES_IN_JAVADOC) {
        final PsiElement containingComment = PsiTreeUtil.getParentOfType(reference, PsiDocComment.class);
        if (containingComment != null) {
          return;
        }
      }
      final PsiFile containingFile = reference.getContainingFile();
      if (!(containingFile instanceof PsiJavaFile)) {
        return;
      }
      final PsiElement target = reference.resolve();
      if (!(target instanceof PsiClass)) {
        return;
      }
      final PsiJavaCodeReferenceElement qualifierReference = (PsiJavaCodeReferenceElement)qualifier;
      final PsiElement qualifierTarget = qualifierReference.resolve();
      if (!(qualifierTarget instanceof PsiPackage)) {
        return;
      }
      final List<PsiJavaCodeReferenceElement> references = new ArrayList(2);
      references.add(reference);
      if (styleSettings.INSERT_INNER_CLASS_IMPORTS) {
        collectInnerClassNames(reference, references);
      }
      Collections.reverse(references);
      for (final PsiJavaCodeReferenceElement aReference : references) {
        final PsiElement referenceTarget = aReference.resolve();
        if (!(referenceTarget instanceof PsiClass)) {
          continue;
        }
        final PsiClass aClass = (PsiClass)referenceTarget;
        final String qualifiedName = aClass.getQualifiedName();
        if (qualifiedName == null) {
          continue;
        }
        if (!ImportUtils.nameCanBeImported(qualifiedName, reference)) {
          continue;
        }
        final boolean inSameFile = aClass.getContainingFile() == containingFile;
        registerError(aReference, Boolean.valueOf(inSameFile));
        break;
      }
    }

    private static void collectInnerClassNames(PsiJavaCodeReferenceElement reference, List<PsiJavaCodeReferenceElement> references) {
      PsiElement rParent = reference.getParent();
      while (rParent instanceof PsiJavaCodeReferenceElement) {
        final PsiJavaCodeReferenceElement parentReference = (PsiJavaCodeReferenceElement)rParent;
        if (!reference.equals(parentReference.getQualifier())) {
          break;
        }
        references.add(parentReference);
        rParent = rParent.getParent();
      }
    }
  }
}