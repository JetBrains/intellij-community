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
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Max Medvedev
 */
public class ClashingGettersInspection extends BaseInspection {
  @Nls
  @NotNull
  public String getGroupDisplayName() {
    return CONFUSING_CODE_CONSTRUCTS;
  }

  @Nls
  @NotNull
  public String getDisplayName() {
    return "Clashing getters";
  }

  @Nullable
  protected String buildErrorString(Object... args) {
    return GroovyInspectionBundle.message("getter.0.clashes.with.getter.1", args);
  }

  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitTypeDefinition(GrTypeDefinition typeDefinition) {
        final GrMethod[] methods = typeDefinition.getGroovyMethods();

        Map<String, GrMethod> getters = new HashMap<String, GrMethod>();
        for (GrMethod method : methods) {
          final String methodName = method.getName();
          if (!GroovyPropertyUtils.isGetterName(methodName)) continue;

          final String propertyName = GroovyPropertyUtils.getPropertyNameByGetterName(methodName, true);

          final GrMethod otherGetter = getters.get(propertyName);
          if (otherGetter != null && !methodName.equals(otherGetter.getName())) {
            registerError(otherGetter.getNameIdentifierGroovy(), otherGetter.getName(), methodName);
            registerError(method.getNameIdentifierGroovy(), methodName, otherGetter.getName());
          }
          else {
            getters.put(propertyName, method);
          }
        }
      }
    };
  }
}
