/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.fixes;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.RefactoringQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.JavaRefactoringActionHandlerFactory;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.encapsulateFields.*;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class EncapsulateVariableFix extends RefactoringInspectionGadgetsFix implements RefactoringQuickFix {

  private final String fieldName;

  public EncapsulateVariableFix(String fieldName) {
    this.fieldName = fieldName;
  }

  @Override
  @NotNull
  public String getName() {
    return InspectionGadgetsBundle.message("encapsulate.variable.quickfix", fieldName);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("encapsulate.variable.fix.family.name");
  }

  @Override
  public PsiElement getElementToRefactor(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (parent instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)parent;
      final PsiElement target = referenceExpression.resolve();
      assert target instanceof PsiField;
      return target;
    }
    else {
      return super.getElementToRefactor(element);
    }
  }

  @NotNull
  @Override
  public RefactoringActionHandler getHandler() {
    return JavaRefactoringActionHandlerFactory.getInstance().createEncapsulateFieldsHandler();
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
    EncapsulateOnPreviewProcessor processor = getProcessor(project, previewDescriptor);
    if (processor == null) {
      return IntentionPreviewInfo.EMPTY;
    }
    processor.performRefactoring();
    return IntentionPreviewInfo.DIFF;
  }

  private @Nullable EncapsulateOnPreviewProcessor getProcessor(Project project, @NotNull ProblemDescriptor previewDescriptor) {
    PsiField field = ObjectUtils.tryCast(getElementToRefactor(previewDescriptor.getPsiElement()), PsiField.class);
    if (field == null || field.getContainingClass() == null) {
      return null;
    }
    FieldDescriptor fieldDescriptor = new FieldDescriptorImpl(field, GenerateMembersUtil.suggestGetterName(field),
                                                              GenerateMembersUtil.suggestSetterName(field),
                                                              GenerateMembersUtil.generateGetterPrototype(field),
                                                              GenerateMembersUtil.generateSetterPrototype(field));
    return new EncapsulateOnPreviewProcessor(project, fieldDescriptor);
  }

  static class EncapsulateOnPreviewProcessor extends EncapsulateFieldsProcessor {
    private final FieldDescriptor myFieldDescriptor;

    EncapsulateOnPreviewProcessor(Project project, FieldDescriptor fieldDescriptor) {
      super(project, new EncapsulateOnPreviewDescriptor(fieldDescriptor));
      myFieldDescriptor = fieldDescriptor;
    }

    public void performRefactoring() {
      performRefactoring(findUsages());
    }

    @Override
    public UsageInfo @NotNull [] findUsages() {
      ArrayList<EncapsulateFieldUsageInfo> array = new ArrayList<>();
      for (PsiReference reference : findReferences(myFieldDescriptor.getField())) {
        checkReference(reference, myFieldDescriptor, array);
      }
      UsageInfo[] usageInfos = array.toArray(UsageInfo.EMPTY_ARRAY);
      return UsageViewUtil.removeDuplicatedUsages(usageInfos);
    }

    @NotNull
    private static Iterable<PsiReference> findReferences(PsiField field) {
      return SyntaxTraverser.psiTraverser(field.getContainingFile()).filter(PsiReference.class).filter(ref -> ref.isReferenceTo(field));
    }
  }

  static class EncapsulateOnPreviewDescriptor implements EncapsulateFieldsDescriptor {

    private final FieldDescriptor myFieldDescriptor;

    EncapsulateOnPreviewDescriptor(FieldDescriptor fieldDescriptor) {
      myFieldDescriptor = fieldDescriptor;
    }

    @Override
    public FieldDescriptor[] getSelectedFields() {
      return new FieldDescriptor[]{myFieldDescriptor};
    }

    @Override
    public boolean isToEncapsulateGet() {
      return true;
    }

    @Override
    public boolean isToEncapsulateSet() {
      return true;
    }

    @Override
    public boolean isToUseAccessorsWhenAccessible() {
      return true;
    }

    @Override
    public String getFieldsVisibility() {
      return PsiModifier.PRIVATE;
    }

    @Override
    public String getAccessorsVisibility() {
      return PsiModifier.PUBLIC;
    }

    @Override
    public int getJavadocPolicy() {
      return DocCommentPolicy.MOVE;
    }

    @Override
    public PsiClass getTargetClass() {
      return myFieldDescriptor.getField().getContainingClass();
    }
  }
}
