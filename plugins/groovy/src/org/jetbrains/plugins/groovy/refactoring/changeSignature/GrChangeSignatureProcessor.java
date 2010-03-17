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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.changeSignature.ChangeInfo;
import com.intellij.refactoring.changeSignature.ChangeSignatureViewDescriptor;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.Collection;
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
    final Collection<PsiReference> collection = MethodReferencesSearch.search(myInfo.getMethod()).findAll();
    return new UsageInfo[0];  //To change body of implemented methods use File | Settings | File Templates.
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

  static class GrChangeInfoImpl implements ChangeInfo {
    private GrMethod myMethod;
    private String myNewName;
    private CanonicalTypes.Type myReturnType;
    private String myVisibilityModifier;
    private List<GrParameterInfo> myParameters;
    private boolean myChangeParameters = false;

    public GrChangeInfoImpl(GrMethod method,
                             String visibilityModifier,
                             CanonicalTypes.Type returnType,
                             String newName,
                             List<GrParameterInfo> parameters) {
      myMethod = method;
      myVisibilityModifier = visibilityModifier;
      myReturnType = returnType;
      myParameters = parameters;
      myNewName = newName;

      final int oldParameterCount = myMethod.getParameters().length;
      if (oldParameterCount != myParameters.size()) {
        myChangeParameters = true;
      }

      for (int i = 0, parametersSize = parameters.size(); i < parametersSize; i++) {
        GrParameterInfo parameter = parameters.get(i);
        if (parameter.getOldIndex() != i) {
          myChangeParameters = true;
        }
      }
    }

    @NotNull
    public ParameterInfo[] getNewParameters() {
      return myParameters.toArray(new GrParameterInfo[myParameters.size()]);
    }

    public boolean isParameterSetOrOrderChanged() {
      return myChangeParameters;
    }

    public PsiMethod getMethod() {
      return myMethod;
    }

    public boolean isChangeVisibility() {
      return !myMethod.getModifierList().hasModifierProperty(myVisibilityModifier);
    }

    public boolean isChangeName() {
      return !myMethod.getName().equals(myNewName);
    }

    public String getNewName() {
      return myNewName;
    }

    public String getVisibilityModifier() {
      return myVisibilityModifier;
    }
  }
}
