/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.JavaChangeSignatureDialog;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PairFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.intentions.base.Intention;
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrParametersOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.refactoring.changeSignature.GrChangeSignatureDialog;
import org.jetbrains.plugins.groovy.refactoring.changeSignature.GrMethodDescriptor;
import org.jetbrains.plugins.groovy.refactoring.changeSignature.GrParameterInfo;
import org.jetbrains.plugins.groovy.refactoring.convertToJava.GroovyToJavaGenerator;
import org.jetbrains.plugins.groovy.refactoring.ui.MethodOrClosureScopeChooser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class CreateParameterFromUsageFix extends Intention implements MethodOrClosureScopeChooser.JBPopupOwner {
  private final String myName;
  private JBPopup myEnclosingMethodsPopup = null;

  public CreateParameterFromUsageFix(GrReferenceExpression ref) {
    myName = ref.getReferenceName();
  }

  @NotNull
  @Override
  public String getText() {
    return GroovyBundle.message("create.parameter.from.usage", myName);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyBundle.message("create.from.usage.family.name");
  }

  @Override
  public JBPopup get() {
    return myEnclosingMethodsPopup;
  }

  @Override
  protected void processIntention(@NotNull PsiElement element, Project project, Editor editor) throws IncorrectOperationException {
    if (element instanceof GrReferenceExpression) {
      findScope((GrReferenceExpression)element, editor, project);
    }
  }

  @Override
  protected boolean isStopElement(PsiElement element) {
    return element instanceof GrExpression;
  }

  @NotNull
  @Override
  protected PsiElementPredicate getElementPredicate() {
    return new PsiElementPredicate() {
      @Override
      public boolean satisfiedBy(PsiElement element) {
        return element instanceof GrReferenceExpression;
      }
    };
  }

  private void findScope(@NotNull final GrReferenceExpression ref, @NotNull final Editor editor, final Project project) {
    PsiElement place = ref;
    final List<GrMethod> scopes = new ArrayList<>();
    while (true) {
      final GrMethod parent = PsiTreeUtil.getParentOfType(place, GrMethod.class);
      if (parent == null) break;
      scopes.add(parent);
      place = parent;
    }

    if (scopes.size() == 1) {
      final GrMethod owner = scopes.get(0);
      final PsiMethod toSearchFor;
      toSearchFor = SuperMethodWarningUtil.checkSuperMethod(owner, RefactoringBundle.message("to.refactor"));
      if (toSearchFor == null) return; //if it is null, refactoring was canceled
      showDialog(toSearchFor, ref, project);
    }
    else if (scopes.size() > 1) {
      myEnclosingMethodsPopup = MethodOrClosureScopeChooser.create(scopes, editor, this, (owner, element) -> {
        showDialog((PsiMethod)owner, ref, project);
        return null;
      });
      myEnclosingMethodsPopup.showInBestPositionFor(editor);
    }
  }

  private static void showDialog(final PsiMethod method, final GrReferenceExpression ref, final Project project) {
    final String name = ref.getReferenceName();
    final List<PsiType> types = GroovyExpectedTypesProvider.getDefaultExpectedTypes(ref);

    PsiType unboxed = types.isEmpty() ? null : TypesUtil.unboxPrimitiveTypeWrapper(types.get(0));
    @NotNull final PsiType type = unboxed != null ? unboxed : PsiType.getJavaLangObject(ref.getManager(), ref.getResolveScope());

    if (method instanceof GrMethod) {
      GrMethodDescriptor descriptor = new GrMethodDescriptor((GrMethod)method);
      GrChangeSignatureDialog dialog = new GrChangeSignatureDialog(project, descriptor, true, ref);

      List<GrParameterInfo> parameters = dialog.getParameters();
      parameters.add(createParameterInfo(name, type));
      dialog.setParameterInfos(parameters);
      dialog.show();
    }
    else if (method != null) {
      JavaChangeSignatureDialog dialog = new JavaChangeSignatureDialog(project, method, false, ref);
      final List<ParameterInfoImpl> parameterInfos = new ArrayList<>(Arrays.asList(ParameterInfoImpl.fromMethod(method)));
      ParameterInfoImpl parameterInfo = new ParameterInfoImpl(-1, name, type, PsiTypesUtil.getDefaultValueOfType(type), false);
      if (!method.isVarArgs()) {
        parameterInfos.add(parameterInfo);
      }
      else {
        parameterInfos.add(parameterInfos.size() - 1, parameterInfo);
      }
      dialog.setParameterInfos(parameterInfos);
      dialog.show();
    }
  }

  private static GrParameterInfo createParameterInfo(String name, PsiType type) {
    String notNullName = name != null ? name : "";
    String defaultValueText = GroovyToJavaGenerator.getDefaultValueText(type.getCanonicalText());
    return new GrParameterInfo(notNullName, defaultValueText, "", type, -1, false);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
