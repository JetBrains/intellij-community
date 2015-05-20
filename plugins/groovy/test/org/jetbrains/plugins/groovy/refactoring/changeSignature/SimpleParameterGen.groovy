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
package org.jetbrains.plugins.groovy.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod

/**
 * @author Max Medvedev
 */
class SimpleParameterGen implements ChangeSignatureTestCase.GenParams {
  private final List<SimpleInfo> myInfos;
  private Project myProject;

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

      final SimpleInfo sim = myInfos[i];
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
      if (sim.myType != null && sim.myType.length() > 0) {
        type = JavaPsiFacade.getElementFactory(myProject).createTypeFromText(sim.myType, method);
      }
      if (sim.myDefaultInitializer != null) {
        defInitializer = sim.myDefaultInitializer;
      }
      if (sim.myDefaultValue != null) {
        defValue = sim.myDefaultValue;
      }

      assert oldIndex >= 0 || defValue != null || defInitializer != null;
      assert name != null;
      info = new GrParameterInfo(name, defValue, defInitializer, type, oldIndex, sim.myFeelLucky);
      result[i] = info;
    }
    return result;
  }
}
