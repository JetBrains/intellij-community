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
package org.jetbrains.plugins.groovy.codeInspection.local;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.GroovyIntentionsBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.refactoring.changeSignature.GrChangeInfoImpl;
import org.jetbrains.plugins.groovy.refactoring.changeSignature.GrChangeSignatureProcessor;
import org.jetbrains.plugins.groovy.refactoring.changeSignature.GrParameterInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class RemoveUnusedGrParameterFix implements IntentionAction {
  private final String myName;

  public RemoveUnusedGrParameterFix(GrParameter parameter) {
    myName = parameter.getName();
  }

  @NotNull
  @Override
  public String getText() {
    return GroovyIntentionsBundle.message("remove.parameter.0", myName);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return GroovyIntentionsBundle.message("remove.unused.parameter");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiElement at = file.findElementAt(editor.getCaretModel().getOffset());
    GrParameter parameter = PsiTreeUtil.getParentOfType(at, GrParameter.class);

    return parameter != null && myName.equals(parameter.getName());
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElement at = file.findElementAt(editor.getCaretModel().getOffset());
    GrParameter parameter = PsiTreeUtil.getParentOfType(at, GrParameter.class);
    if (parameter == null) return;

    if (!FileModificationService.getInstance().prepareFileForWrite(parameter.getContainingFile())) return;

    GrMethod method = (GrMethod)parameter.getDeclarationScope();
    GrChangeSignatureProcessor processor = new GrChangeSignatureProcessor(parameter.getProject(), createChangeInfo(method, parameter));
    processor.run();
  }

  private static GrChangeInfoImpl createChangeInfo(GrMethod method, GrParameter parameter) {
    List<GrParameterInfo> params = new ArrayList<>();
    int i = 0;
    for (GrParameter p : method.getParameterList().getParameters()) {
      if (p != parameter) {
        params.add(new GrParameterInfo(p, i));
      }
      i++;
    }

    GrTypeElement typeElement = method.getReturnTypeElementGroovy();
    CanonicalTypes.Type wrapper = typeElement != null ? CanonicalTypes.createTypeWrapper(method.getReturnType()) : null;
    return new GrChangeInfoImpl(method, null, wrapper, method.getName(), params, null, false);
  }


  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
