/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.ImportHelper;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ImportUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public class UnnecessaryFullyQualifiedNameInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @SuppressWarnings({"PublicField", "unused"})
  public boolean m_ignoreJavadoc; // left here to prevent changes to project files.

  public boolean ignoreInModuleStatements = true;

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
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreInModuleStatements", InspectionGadgetsBundle.message("ignore.in.module.statements.option")));
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    return new UnnecessaryFullyQualifiedNameFix(((Boolean)infos[0]).booleanValue());
  }

  private static class UnnecessaryFullyQualifiedNameFix extends ModCommandQuickFix {

    private final boolean inSameFile;

    UnnecessaryFullyQualifiedNameFix(boolean inSameFile) {
      this.inSameFile = inSameFile;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.fix.family.name");
    }

    @Override
    @NotNull
    public String getName() {
      return inSameFile
             ? InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.remove.quickfix")
             : InspectionGadgetsBundle.message("unnecessary.fully.qualified.name.replace.quickfix");
    }

    @Override
    public final @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      return ModCommands.psiUpdate(descriptor.getStartElement(), (element, updater) -> {
        final PsiJavaCodeReferenceElement referenceElement;
        if (descriptor.getHighlightType() == ProblemHighlightType.INFORMATION) {
          referenceElement = (PsiJavaCodeReferenceElement)element;
        }
        else {
          referenceElement = (PsiJavaCodeReferenceElement)element.getParent();
        }
        applyFix(referenceElement, updater);
      });
    }

    private static void applyFix(@NotNull PsiJavaCodeReferenceElement referenceElement, @NotNull ModPsiUpdater updater) {
      final PsiFile file = referenceElement.getContainingFile();
      final PsiElement target = referenceElement.resolve();
      if (!(target instanceof PsiClass aClass)) {
        return;
      }
      final String qualifiedName = aClass.getQualifiedName();
      if (qualifiedName == null || !ImportUtils.nameCanBeImported(qualifiedName, referenceElement)) {
        return;
      }
      ImportUtils.addImportIfNeeded(aClass, referenceElement);
      final String fullyQualifiedText = referenceElement.getText();
      final QualificationRemover qualificationRemover = new QualificationRemover(fullyQualifiedText);
      file.accept(qualificationRemover);
      qualificationRemover.getShortenedElements().forEach(updater::highlight);
    }
  }

  public static class QualificationRemover extends JavaRecursiveElementWalkingVisitor {
    private final String fullyQualifiedText;
    private final List<PsiElement> shortenedElements = new ArrayList<>();

    public QualificationRemover(String fullyQualifiedText) {
      this.fullyQualifiedText = fullyQualifiedText;
    }

    @NotNull
    public Collection<PsiElement> getShortenedElements() {
      return Collections.unmodifiableCollection(shortenedElements);
    }

    @Override
    public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      final PsiElement parent = PsiTreeUtil.getParentOfType(reference, PsiImportStatementBase.class);
      if (parent != null) {
        return;
      }
      final String text = reference.getText();
      if (!text.equals(fullyQualifiedText)) {
        return;
      }
      final PsiDocComment containingComment = PsiTreeUtil.getParentOfType(reference, PsiDocComment.class);
      if (containingComment != null) {
        final PsiFile file = reference.getContainingFile();
        if ("package-info.java".equals(file.getName())) {
          return;
        }
        final JavaCodeStyleSettings javaSettings = JavaCodeStyleSettings.getInstance(reference.getContainingFile());
        if (javaSettings.useFqNamesInJavadocAlways()) {
          return;
        }
      }
      final PsiElement qualifier = reference.getQualifier();
      if (qualifier == null) {
        return;
      }
      new CommentTracker().deleteAndRestoreComments(qualifier);
      shortenedElements.add(reference);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryFullyQualifiedNameVisitor();
  }

  private class UnnecessaryFullyQualifiedNameVisitor extends BaseInspectionVisitor {

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      checkReference(expression);
    }

    @Override
    public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);
      checkReference(reference);
    }

    private void checkReference(PsiJavaCodeReferenceElement reference) {
      final PsiElement qualifier = reference.getQualifier();
      if (!(qualifier instanceof PsiJavaCodeReferenceElement qualifierReference)) {
        return;
      }
      final PsiElement parent = reference.getParent();
      if (parent instanceof PsiMethodCallExpression || parent instanceof PsiAssignmentExpression || parent instanceof PsiVariable) {
        return;
      }
      final PsiElement element =
        PsiTreeUtil.getParentOfType(reference, PsiImportStatementBase.class, PsiPackageStatement.class,
                                    JavaCodeFragment.class, PsiAnnotation.class);
      if (element != null && !(element instanceof PsiAnnotation)) {
        return;
      }
      if (!(reference.getContainingFile() instanceof PsiJavaFile containingFile)) {
        return;
      }
      final PsiElement target = reference.resolve();
      if (!(target instanceof PsiClass)) {
        return;
      }
      final CodeStyleSettings styleSettings = CodeStyle.getSettings(containingFile);
      final PsiDocComment containingComment = PsiTreeUtil.getParentOfType(reference, PsiDocComment.class);
      boolean reportAsInformationInsideJavadoc = false;
      if (containingComment != null) {
        if (acceptFqnInJavadoc(containingFile, styleSettings)) {
          return;
        }
        JavaCodeStyleSettings javaSettings = styleSettings.getCustomSettings(JavaCodeStyleSettings.class);
        if (javaSettings.CLASS_NAMES_IN_JAVADOC == JavaCodeStyleSettings.FULLY_QUALIFY_NAMES_IF_NOT_IMPORTED) {
          reportAsInformationInsideJavadoc = !ImportHelper.isAlreadyImported(containingFile, reference.getQualifiedName());
        }
      }
      final PsiElement qualifierTarget = qualifierReference.resolve();
      if (!(qualifierTarget instanceof PsiPackage)) {
        return;
      }
      final List<PsiJavaCodeReferenceElement> references = new SmartList<>();
      references.add(reference);
      if (styleSettings.getCustomSettings(JavaCodeStyleSettings.class).INSERT_INNER_CLASS_IMPORTS) {
        collectInnerClassNames(reference, references);
      }
      Collections.reverse(references);
      PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(containingFile.getProject()).getResolveHelper();
      for (final PsiJavaCodeReferenceElement aReference : references) {
        final PsiElement referenceTarget = aReference.resolve();
        if (!(referenceTarget instanceof PsiClass aClass)) {
          continue;
        }
        final String qualifiedName = aClass.getQualifiedName();
        if (qualifiedName == null) {
          continue;
        }
        if (!resolveHelper.isAccessible(aClass, containingFile, null)) {
          continue;
        }
        if (!ImportUtils.nameCanBeImported(qualifiedName, reference)) {
          continue;
        }
        final PsiElement qualifier1 = aReference.getQualifier();
        if (qualifier1 != null) {
          Set<String> nonUniqueReferences = getNonUniqueReferencesFromCache(containingFile);
          if (nonUniqueReferences.contains(aReference.getReferenceName())) {
            continue;
          }

          PsiElement elementToHighlight = qualifier1;
          final ProblemHighlightType highlightType;
          if (reportAsInformationInsideJavadoc ||
              ignoreInModuleStatements &&
              PsiTreeUtil.getParentOfType(reference, PsiUsesStatement.class, PsiProvidesStatement.class) != null ||
              InspectionProjectProfileManager.isInformationLevel(getShortName(), aReference) && isOnTheFly()) {
            if (!isOnTheFly()) return;
            highlightType = ProblemHighlightType.INFORMATION;
            elementToHighlight = aReference;
          }
          else {
            highlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
          }

          final boolean inSameFile = aClass.getContainingFile() == containingFile ||
                                     ImportHelper.isAlreadyImported(containingFile, qualifiedName);
          registerError(elementToHighlight, highlightType, inSameFile);
        }
        break;
      }
    }

    private static void collectInnerClassNames(PsiJavaCodeReferenceElement reference,
                                               List<? super PsiJavaCodeReferenceElement> references) {
      PsiElement rParent = reference.getParent();
      while (rParent instanceof PsiJavaCodeReferenceElement parentReference) {
        if (!reference.equals(parentReference.getQualifier())) {
          break;
        }
        references.add(parentReference);
        rParent = rParent.getParent();
      }
    }

    private static boolean acceptFqnInJavadoc(PsiJavaFile javaFile, CodeStyleSettings styleSettings) {
      if ("package-info.java".equals(javaFile.getName())) {
        return true;
      }
      return styleSettings.getCustomSettings(JavaCodeStyleSettings.class).useFqNamesInJavadocAlways();
    }
  }

  private static Set<String> getNonUniqueReferencesFromCache(PsiJavaFile file) {
    return CachedValuesManager.getCachedValue(file, () -> {
      ReferenceCollector referenceCollector = new ReferenceCollector();
      file.accept(referenceCollector);
      return CachedValueProvider.Result.create(referenceCollector.getNonUniqueReferences(), PsiModificationTracker.MODIFICATION_COUNT);
    });
  }

  private static class ReferenceCollector extends JavaRecursiveElementWalkingVisitor {
    private final Map<String, String> referenceByFirstVisitName = new HashMap<>();
    private final Set<String> nonUniqueReferences = new HashSet<>();

    @Override
    public void visitImportList(@NotNull PsiImportList list) { }

    @Override
    public void visitReferenceElement(@NotNull PsiJavaCodeReferenceElement reference) {
      super.visitReferenceElement(reference);

      if (!reference.isQualified()) {
        return;
      }
      final PsiElement target = reference.resolve();
      if (!(target instanceof final PsiClass aClass)) {
        return;
      }
      String qualifiedName = aClass.getQualifiedName();
      String referenceName = reference.getReferenceName();
      if (qualifiedName == null || referenceName == null) {
        return;
      }
      referenceByFirstVisitName.compute(referenceName, (k, element) -> {
        if (element == null) {
          return qualifiedName;
        }
        if (!qualifiedName.equals(element)) {
          nonUniqueReferences.add(referenceName);
        }
        return element;
      });
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      visitReferenceElement(expression);
    }

    public Set<String> getNonUniqueReferences() {
      return nonUniqueReferences;
    }
  }
}