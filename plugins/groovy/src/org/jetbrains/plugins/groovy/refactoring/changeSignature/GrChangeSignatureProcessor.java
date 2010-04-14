/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.changeSignature.ChangeSignatureViewDescriptor;
import com.intellij.refactoring.changeSignature.JavaChangeInfo;
import com.intellij.refactoring.changeSignature.JavaParameterInfo;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GrChangeSignatureProcessor extends BaseRefactoringProcessor {
  private final GrChangeInfoImpl myInfo;

  //private

  public GrChangeSignatureProcessor(Project project, GrChangeInfoImpl changeInfo) {
    super(project);
    myInfo = changeInfo;
  }

  @Override
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
    return new ChangeSignatureViewDescriptor(myInfo.getMethod());
  }

  @NotNull
  @Override
  protected UsageInfo[] findUsages() {
    final PsiReference[] refs = MethodReferencesSearch.search(myInfo.getMethod()).toArray(PsiReference.EMPTY_ARRAY);
    for (PsiReference ref : refs) {

    }

    return UsageInfo.EMPTY_ARRAY;//todo
  }

  @Override
  protected void refreshElements(PsiElement[] elements) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  protected void performRefactoring(UsageInfo[] usages) {
    changeMethod();
  }

  private void changeMethod() {
    final PsiMethod method = myInfo.getMethod();
    if (myInfo.isChangeName()) {
      method.setName(myInfo.getNewName());
    }

    if (myInfo.isChangeVisibility()) {
      method.getModifierList().setModifierProperty(myInfo.getVisibilityModifier(), true);
    }
  }

  @Override
  protected String getCommandName() {
    return "";
    //return RefactoringBundle.message("changing.signature.of.0", UsageViewUtil.getDescriptiveName(myInfo.getMethod()));
  }


  private MultiMap<PsiElement, String> findConflicts(Ref<UsageInfo[]> refUsages) {
    return new MultiMap<PsiElement, String>();//todo
  }

  @Override
  protected boolean preprocessUsages(Ref<UsageInfo[]> refUsages) {
    return showConflicts(findConflicts(refUsages));
  }

  static class GrChangeInfoImpl implements JavaChangeInfo {
    final GrMethod method;
    final String newName;
    final CanonicalTypes.Type returnType;
    final String visibilityModifier;
    final List<GrParameterInfo> parameters;
    boolean changeParameters = false;

    public GrChangeInfoImpl(GrMethod method,
                            String visibilityModifier,
                            CanonicalTypes.Type returnType,
                            String newName,
                            List<GrParameterInfo> parameters) {
      this.method = method;
      this.visibilityModifier = visibilityModifier;
      this.returnType = returnType;
      this.parameters = parameters;
      this.newName = newName;

      final int oldParameterCount = this.method.getParameters().length;
      if (oldParameterCount != this.parameters.size()) {
        changeParameters = true;
      }

      for (int i = 0, parametersSize = parameters.size(); i < parametersSize; i++) {
        GrParameterInfo parameter = parameters.get(i);
        if (parameter.getOldIndex() != i) {
          changeParameters = true;
        }
      }
    }

    @NotNull
    public JavaParameterInfo[] getNewParameters() {
      return parameters.toArray(new GrParameterInfo[parameters.size()]);
    }

    public String getNewVisibility() {
      return null;//todo
    }

    public boolean isParameterSetOrOrderChanged() {
      return changeParameters;
    }

    public boolean isParameterTypesChanged() {
      return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isParameterNamesChanged() {
      return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isGenerateDelegate() {
      return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isNameChanged() {
      return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isVisibilityChanged() {
      return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isExceptionSetChanged() {
      return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isExceptionSetOrOrderChanged() {
      return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public PsiMethod getMethod() {
      return method;
    }

    public boolean isReturnTypeChanged() {
      return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public CanonicalTypes.Type getNewReturnType() {
      return returnType;
    }

    public boolean isChangeVisibility() {
      return !method.getModifierList().hasModifierProperty(visibilityModifier);
    }

    public boolean isChangeName() {
      return !method.getName().equals(newName);
    }

    public String getNewName() {
      return newName;
    }

    public Language getLanguage() {
      return GroovyFileType.GROOVY_LANGUAGE;
    }

    public String getVisibilityModifier() {
      return visibilityModifier;
    }

    @NotNull
    public String[] getOldParameterNames() {
      return new String[0];  //To change body of implemented methods use File | Settings | File Templates.
    }

    @NotNull
    public String[] getOldParameterTypes() {
      return new String[0];  //To change body of implemented methods use File | Settings | File Templates.
    }

    public ThrownExceptionInfo[] getNewExceptions() {
      return new ThrownExceptionInfo[0];  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isRetainsVarargs() {
      return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isObtainsVarags() {
      return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isArrayToVarargs() {
      return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public PsiIdentifier getNewNameIdentifier() {
      return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getOldName() {
      return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean wasVararg() {
      return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean[] toRemoveParm() {
      return new boolean[0];  //To change body of implemented methods use File | Settings | File Templates.
    }
  }
}
