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
package org.jetbrains.plugins.groovy.codeInspection.metrics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.utils.LibraryUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

public class GroovyMethodParameterCountInspectionBase extends GroovyMethodMetricInspection {
  @Override
  @NotNull
  public String getDisplayName() {
    return "Method with too many parameters";
  }

  @Override
  protected int getDefaultLimit() {
    return 5;
  }

  @Override
  protected String getConfigurationLabel() {
    return "Maximum number of parameters:";
  }

  @Override
  public String buildErrorString(Object... args) {
    return "Method '#ref' contains too many parameters (" + args[0] + '>' + args[1] + ')';
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private class Visitor extends BaseInspectionVisitor {
    @Override
    public void visitMethod(@NotNull GrMethod grMethod) {
      super.visitMethod(grMethod);
      final GrParameter[] parameters = grMethod.getParameters();
      final int limit = getLimit();
      if (parameters == null || parameters.length <= limit) {
        return;
      }
      if (LibraryUtil.isOverrideOfLibraryMethod(grMethod)) {
        return;
      }
      registerMethodError(grMethod, parameters.length, limit);
    }
  }
}
