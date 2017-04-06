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
package com.siyeh.ipp.functional;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.codeInspection.LambdaCanBeMethodReferenceInspection;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.extractMethod.AbstractExtractDialog;
import com.intellij.refactoring.extractMethod.ControlFlowWrapper;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import com.intellij.refactoring.util.VariableData;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.text.UniqueNameGenerator;
import com.siyeh.IntentionPowerPackBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public class ExtractToMethodReferenceIntention extends BaseElementAtCaretIntentionAction {
  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return IntentionPowerPackBundle.message("extract.to.method.reference.intention.name");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class, false);
    if (lambdaExpression != null) {
      PsiElement body = lambdaExpression.getBody();
      if (body == null) return false;
      PsiType functionalInterfaceType = lambdaExpression.getFunctionalInterfaceType();
      if (functionalInterfaceType == null) return false;
      PsiExpression asMethodReference = LambdaCanBeMethodReferenceInspection
        .canBeMethodReferenceProblem(body, lambdaExpression.getParameterList().getParameters(), functionalInterfaceType, null);
      if (asMethodReference != null) return false;
      try {
        PsiElement[] toExtract = body instanceof PsiCodeBlock ? ((PsiCodeBlock)body).getStatements() : new PsiElement[] {body};
        ControlFlowWrapper wrapper = new ControlFlowWrapper(project, body, toExtract);
        wrapper.prepareExitStatements(toExtract, body);
        PsiVariable[] outputVariables = wrapper.getOutputVariables();
        List<PsiVariable> inputVariables = wrapper.getInputVariables(body, toExtract, outputVariables);
        return inputVariables.stream().allMatch(variable -> variable instanceof PsiParameter && ((PsiParameter)variable).getDeclarationScope() == lambdaExpression);
      }
      catch (PrepareFailedException ignored) { }
      catch (ControlFlowWrapper.ExitStatementsNotSameException ignored) { }
    }
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiLambdaExpression lambdaExpression = PsiTreeUtil.getParentOfType(element, PsiLambdaExpression.class, false);
    if (lambdaExpression != null) {
      PsiElement body = lambdaExpression.getBody();
      if (body != null) {
        PsiElement[] elements = body instanceof PsiCodeBlock ? ((PsiCodeBlock)body).getStatements() : new PsiElement[] {body};
        PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(lambdaExpression.getFunctionalInterfaceType());
        String initialMethodName = interfaceMethod != null ? interfaceMethod.getName() : "name";
        ExtractMethodProcessor methodProcessor = new ExtractMethodProcessor(project, editor, elements, null, getFamilyName(), null, null) {
          @Override
          public boolean showDialog() {
            apply(new MyExtractMethodDialog(myTargetClass, lambdaExpression, myCanBeStatic, initialMethodName));
            return true;
          }
        };

        try {
          methodProcessor.prepare();
        }
        catch (PrepareFailedException e) {
          return;
        }
        methodProcessor.showDialog();
        WriteAction.run(() -> {
          methodProcessor.doExtract();
          PsiExpression expression = LambdaCanBeMethodReferenceInspection.replaceLambdaWithMethodReference(lambdaExpression);
          if (expression instanceof PsiMethodReferenceExpression) {
            PsiMethod method = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(methodProcessor.getExtractedMethod());

            PsiElement refElement = ((PsiMethodReferenceExpression)expression).getReferenceNameElement();
            PsiIdentifier nameIdentifier = method.getNameIdentifier();
            if (nameIdentifier == null) return;

            //try to navigate to reference name
            editor.getCaretModel().moveToOffset(ObjectUtils.notNull(refElement, nameIdentifier).getTextOffset());

            final RenamePsiElementProcessor processor = RenamePsiElementProcessor.forElement(method);
            if (!processor.isInplaceRenameSupported()) {
              return;
            }
            List<String> suggestedNames = new ArrayList<>();
            suggestedNames.add(method.getName());
            processor.substituteElementToRename(method, editor, new Pass<PsiElement>() {
              @Override
              public void pass(PsiElement substitutedElement) {
                final MemberInplaceRenamer renamer = new MemberInplaceRenamer(method, substitutedElement, editor);
                final LinkedHashSet<String> nameSuggestions = new LinkedHashSet<>(suggestedNames);
                renamer.performInplaceRefactoring(nameSuggestions);
              }
            });
          }
        });
      }
    }
  }

  @Nullable
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    return currentFile;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  private static class MyExtractMethodDialog implements AbstractExtractDialog {
    private final String myTargetMethodName;
    private final boolean myCanBeStatic;
    private final VariableData[] myVariableData;

    public MyExtractMethodDialog(@NotNull PsiClass targetClass,
                                 PsiLambdaExpression lambdaExpression,
                                 boolean canBeStatic,
                                 String initialMethodName) {
      myVariableData = Arrays.stream(lambdaExpression.getParameterList().getParameters())
        .map(parameter -> {
          VariableData data = new VariableData(parameter);
          data.passAsParameter = true;
          data.name = parameter.getName();
          return data;
        })
        .toArray(VariableData[]::new);
      myCanBeStatic = canBeStatic;
      PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(targetClass.getProject());
      String parameters = "(" + StringUtil.join(myVariableData, data -> data.type.getCanonicalText() + " " + data.name, ", ") + "){}";
      myTargetMethodName = UniqueNameGenerator.generateUniqueName(initialMethodName,
                                                                  methodName -> {
                                                                    String methodText = "private void " + methodName + parameters;
                                                                    PsiMethod patternMethod = elementFactory.createMethodFromText(methodText, lambdaExpression);
                                                                    return targetClass.findMethodBySignature(patternMethod, true) == null;
                                                                  });

    }

    @Override
    public String getChosenMethodName() {
      return myTargetMethodName;
    }

    @Override
    public VariableData[] getChosenParameters() {
      return myVariableData;
    }

    @NotNull
    @Override
    public String getVisibility() {
      return PsiModifier.PRIVATE;
    }

    @Override
    public boolean isMakeStatic() {
      return myCanBeStatic;
    }

    @Override
    public boolean isChainedConstructor() {
      return false;
    }

    @Override
    public PsiType getReturnType() {
      return null;
    }

    @Override
    public void show() {}

    @Override
    public boolean isOK() {
      return true;
    }
  }
}

