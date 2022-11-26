// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.HighlightUtils;
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

/**
 * @author Bas Leijdekkers
 */
public class UnqualifiedInnerClassAccessInspection extends BaseInspection implements CleanupLocalInspectionTool{

  @SuppressWarnings({"PublicField"})
  public boolean ignoreReferencesToLocalInnerClasses = true;

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("unqualified.inner.class.access.option"),
                                          this, "ignoreReferencesToLocalInnerClasses");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new UnqualifiedInnerClassAccessFix();
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unqualified.inner.class.access.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnqualifiedInnerClassAccessVisitor();
  }

  private static class UnqualifiedInnerClassAccessFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "unqualified.inner.class.access.quickfix");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiJavaCodeReferenceElement)) {
        return;
      }
      final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)element;
      final PsiElement target = referenceElement.resolve();
      if (!(target instanceof PsiClass)) {
        return;
      }
      final PsiClass innerClass = (PsiClass)target;
      final PsiClass containingClass = innerClass.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String qualifiedName = containingClass.getQualifiedName();
      if (qualifiedName == null) {
        return;
      }
      final PsiFile containingFile = referenceElement.getContainingFile();
      if (!(containingFile instanceof PsiJavaFile)) {
        return;
      }
      final PsiJavaFile javaFile = (PsiJavaFile)containingFile;
      final String innerClassName = innerClass.getQualifiedName();
      if (innerClassName == null) {
        return;
      }
      final PsiImportList importList = javaFile.getImportList();
      if (importList == null) {
        return;
      }
      final PsiImportStatement[] importStatements = importList.getImportStatements();
      final int importStatementsLength = importStatements.length;
      boolean onDemand = false;
      PsiImportStatement referenceImportStatement = null;
      for (int i = importStatementsLength - 1; i >= 0; i--) {
        final PsiImportStatement importStatement = importStatements[i];
        final String importString = importStatement.getQualifiedName();
        if (importStatement.isOnDemand()) {
          if (qualifiedName.equals(importString)) {
            referenceImportStatement = importStatement;
            onDemand = true;
            break;
          }
        }
        else {
          if (innerClassName.equals(importString)) {
            referenceImportStatement = importStatement;
            break;
          }
        }
      }
      final ReferenceCollector referenceCollector = new ReferenceCollector(onDemand ? qualifiedName : innerClassName, onDemand);
      javaFile.accept(referenceCollector);

      final Collection<PsiJavaCodeReferenceElement> references = referenceCollector.getReferences();
      final SmartPointerManager pointerManager = SmartPointerManager.getInstance(project);
      final List<SmartPsiElementPointer> pointers = new ArrayList<>();
      for (PsiJavaCodeReferenceElement reference : references) {
        final SmartPsiElementPointer<PsiJavaCodeReferenceElement> pointer = pointerManager.createSmartPsiElementPointer(reference);
        pointers.add(pointer);
      }
      if (referenceImportStatement != null) {
        referenceImportStatement.delete();
      }
      final PsiClass outerClass = ClassUtils.getOutermostContainingClass(containingClass);
      ImportUtils.addImportIfNeeded(outerClass, referenceElement);
      final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
      final Document document = containingFile.getViewProvider().getDocument();
      if (document == null) {
        return;
      }
      documentManager.doPostponedOperationsAndUnblockDocument(document);
      final String text = buildNewText(javaFile, references, containingClass, new StringBuilder()).toString();
      document.replaceString(0, document.getTextLength(), text);
      documentManager.commitDocument(document);
      if (pointers.size() > 1) {
        final List<PsiElement> elements = new ArrayList<>();
        for (SmartPsiElementPointer pointer : pointers) {
          PsiElement psiElement = pointer.getElement();
          if (psiElement != null) {
            elements.add(psiElement);
          }
        }
        if (isOnTheFly()) {
          HighlightUtils.highlightElements(elements);
        }
      }
    }

    private static StringBuilder buildNewText(PsiElement element, Collection<PsiJavaCodeReferenceElement> references,
                                              PsiClass aClass, StringBuilder out) {
      if (element == null) {
        return out;
      }
      //noinspection SuspiciousMethodCalls
      if (references.contains(element)) {
        final String shortClassName = getShortClassName(aClass, new StringBuilder()).toString();
        if (isReferenceToTargetClass(shortClassName, aClass, element)) {
          out.append(shortClassName);
        }
        else {
          out.append(aClass.getQualifiedName());
        }
        out.append('.');
        return out.append(element.getText());
      }
      final PsiElement[] children = element.getChildren();
      if (children.length == 0) {
        return out.append(element.getText());
      }
      for (PsiElement child : children) {
        buildNewText(child, references, aClass, out);
      }
      return out;
    }

    private static StringBuilder getShortClassName(@NotNull PsiClass aClass, @NotNull StringBuilder builder) {
      final PsiClass containingClass = aClass.getContainingClass();
      if (containingClass != null) {
        getShortClassName(containingClass, builder);
        builder.append('.');
      }
      builder.append(aClass.getName());
      return builder;
    }

    private static boolean isReferenceToTargetClass(String referenceText, PsiClass targetClass, PsiElement context) {
      final PsiManager manager = targetClass.getManager();
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(manager.getProject());
      final PsiResolveHelper resolveHelper = facade.getResolveHelper();
      final PsiClass referencedClass = resolveHelper.resolveReferencedClass(referenceText, context);
      if (referencedClass == null) {
        return true;
      }
      return manager.areElementsEquivalent(targetClass, referencedClass);
    }
  }

  private static class ReferenceCollector extends JavaRecursiveElementWalkingVisitor {

    private final String name;
    private final boolean onDemand;
    private final Set<PsiJavaCodeReferenceElement> references = new HashSet<>();

    ReferenceCollector(String name, boolean onDemand) {
      this.name = name;
      this.onDemand = onDemand;
    }

    @Override
    public void visitImportList(@NotNull PsiImportList list) { }

    @Override
    public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      if (reference.isQualified()) {
        return;
      }
      final PsiElement target = reference.resolve();
      if (!(target instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)target;
      if (!onDemand) {
        final String qualifiedName = aClass.getQualifiedName();
        if (name.equals(qualifiedName)) {
          references.add(reference);
        }
        return;
      }
      final PsiClass containingClass = aClass.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final String qualifiedName = containingClass.getQualifiedName();
      if (name.equals(qualifiedName)) {
        references.add(reference);
      }
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    public Collection<PsiJavaCodeReferenceElement> getReferences() {
      return references;
    }
  }

  private class UnqualifiedInnerClassAccessVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      if (reference.isQualified()) {
        return;
      }
      final PsiElement target = reference.resolve();
      if (!(target instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)target;
      if (!aClass.hasModifierProperty(PsiModifier.STATIC) && reference.getParent() instanceof PsiNewExpression) {
          return;
      }
      final PsiClass containingClass = aClass.getContainingClass();
      if (containingClass == null || containingClass instanceof PsiAnonymousClass) {
        return;
      }
      if (ignoreReferencesToLocalInnerClasses) {
        if (PsiTreeUtil.isAncestor(containingClass, reference, true)) {
          return;
        }
        final PsiClass referenceClass = PsiTreeUtil.getParentOfType(reference, PsiClass.class);
        if (referenceClass != null && referenceClass.isInheritor(containingClass, true)) {
          return;
        }
      }
      registerError(reference, containingClass.getName());
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }
  }
}
