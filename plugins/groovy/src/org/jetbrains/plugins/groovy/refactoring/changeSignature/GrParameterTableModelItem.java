// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.debugger.fragments.GroovyCodeFragment;

import static com.intellij.refactoring.changeSignature.ParameterInfo.NEW_PARAMETER;

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
      parameterInfo = new GrParameterInfo("", "", "", null, NEW_PARAMETER, false);
    }

    PsiTypeCodeFragment typeCodeFragment =
      JavaCodeFragmentFactory.getInstance(project).createTypeCodeFragment(parameterInfo.getTypeText(), context, true, JavaCodeFragmentFactory.ALLOW_ELLIPSIS);
    String initializer = parameterInfo.getDefaultInitializer();
    GroovyCodeFragment initializerCodeFragment = new GroovyCodeFragment(project, initializer);
    GroovyCodeFragment defaultValueCodeFragment = new GroovyCodeFragment(project, parameterInfo.getDefaultValue());
    return new GrParameterTableModelItem(parameterInfo, typeCodeFragment, initializerCodeFragment, defaultValueCodeFragment);
  }

  @Override
  public boolean isEllipsisType() {
    try {
      PsiType type = ((PsiTypeCodeFragment)typeCodeFragment).getType();
      return type instanceof PsiArrayType;
    }
    catch (PsiTypeCodeFragment.TypeSyntaxException | PsiTypeCodeFragment.NoTypeException e) {
      return false;
    }
  }
}
