/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.psi.*;
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;

/**
* @author Max Medvedev
*/
public class GrParameterTableModelItem extends ParameterTableModelItemBase<GrParameterInfo> {
  public PsiCodeFragment initializerCodeFragment;

  public GrParameterTableModelItem(GrParameterInfo parameter,
                                   PsiCodeFragment typeCodeFragment,
                                   PsiCodeFragment initializerCodeFragment,
                                   PsiCodeFragment defaultValueCodeFragment) {
    super(parameter, typeCodeFragment, defaultValueCodeFragment);
    this.initializerCodeFragment = initializerCodeFragment;
  }

  public static GrParameterTableModelItem create(@Nullable GrParameterInfo parameterInfo,
                                                 @NotNull final Project project,
                                                 @Nullable final PsiElement context) {
    if (parameterInfo == null) {
      parameterInfo = new GrParameterInfo("", "", "", null, -1, false);
    }

    PsiTypeCodeFragment typeCodeFragment =
      JavaCodeFragmentFactory.getInstance(project).createTypeCodeFragment(parameterInfo.getTypeText(), context, true, JavaCodeFragmentFactory.ALLOW_ELLIPSIS);
    String initializer = parameterInfo.getDefaultInitializer();
    GroovyCodeFragment initializerCodeFragment = new GroovyCodeFragment(project, initializer != null ? initializer : "");
    GroovyCodeFragment defaultValueCodeFragment = new GroovyCodeFragment(project, parameterInfo.getDefaultValue());
    return new GrParameterTableModelItem(parameterInfo, typeCodeFragment, initializerCodeFragment, defaultValueCodeFragment);
  }

  @Override
  public boolean isEllipsisType() {
    try {
      PsiType type = ((PsiTypeCodeFragment)typeCodeFragment).getType();
      return type instanceof PsiArrayType;
    }
    catch (PsiTypeCodeFragment.TypeSyntaxException e) {
      return false;
    }
    catch (PsiTypeCodeFragment.NoTypeException e) {
      return false;
    }
  }
}
