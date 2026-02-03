// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.junit.Assert;

import java.util.List;

/**
 * @author Max Medvedev
 */
public class SimpleParameterGen implements ChangeSignatureTestCase.GenParams {
  public SimpleParameterGen(List<SimpleInfo> infos, Project project) {
    myInfos = infos;
    myProject = project;
  }

  @Override
  public GrParameterInfo[] genParams(GrMethod method) {
    GrParameter[] params = method.getParameterList().getParameters();
    int size = myInfos.size();
    GrParameterInfo[] result = new GrParameterInfo[size];
    for (int i = 0; i < size; i++) {

      final SimpleInfo sim = myInfos.get(i);
      int oldIndex = sim.myOldIndex;

      GrParameterInfo info;
      String name = null;
      String defInitializer = null;
      PsiType type = null;
      String defValue = null;
      if (oldIndex > -1) {
        final GrParameter p = params[oldIndex];
        name = p.getName();
        final GrExpression initializer = p.getInitializerGroovy();
        defInitializer = initializer != null ? initializer.getText() : null;
        type = p.getDeclaredType();
      }

      if (sim.myNewName != null) {
        name = sim.myNewName;
      }

      if (sim.myType != null && !sim.myType.isEmpty()) {
        type = JavaPsiFacade.getElementFactory(myProject).createTypeFromText(sim.myType, method);
      }

      if (sim.myDefaultInitializer != null) {
        defInitializer = sim.myDefaultInitializer;
      }

      if (sim.myDefaultValue != null) {
        defValue = sim.myDefaultValue;
      }

      Assert.assertTrue(oldIndex >= 0 || defValue != null || defInitializer != null);
      Assert.assertNotNull(name);
      info = new GrParameterInfo(name, defValue, defInitializer, type, oldIndex, sim.myFeelLucky);
      result[i] = info;
    }

    return result;
  }

  private final List<SimpleInfo> myInfos;
  private final Project myProject;
}
