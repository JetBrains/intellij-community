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
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Max Medvedev
 */
public class ClashingGettersInspection extends BaseInspection {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return "Clashing getters";
  }

  @Override
  @Nullable
  protected String buildErrorString(Object... args) {
    return GroovyInspectionBundle.message("getter.0.clashes.with.getter.1", args);
  }

  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitTypeDefinition(@NotNull GrTypeDefinition typeDefinition) {
        super.visitTypeDefinition(typeDefinition);

        Map<String, PsiMethod> getters = new HashMap<>();
        for (PsiMethod method : typeDefinition.getMethods()) {
          final String methodName = method.getName();
          if (!GroovyPropertyUtils.isSimplePropertyGetter(method)) continue;

          final String propertyName = GroovyPropertyUtils.getPropertyNameByGetterName(methodName, true);

          final PsiMethod otherGetter = getters.get(propertyName);
          if (otherGetter != null && !methodName.equals(otherGetter.getName())) {
            final Pair<PsiElement, String> description = getGetterDescription(method);
            final Pair<PsiElement, String> otherDescription = getGetterDescription(otherGetter);

            if (description.first != null) {
              registerError(description.first, description.second, otherDescription.second);
            }
            if (otherDescription.first != null) {
              registerError(otherDescription.first, otherDescription.second, description.second);
            }
          }
          else {
            getters.put(propertyName, method);
          }
        }
      }
    };
  }

  private static Pair<PsiElement, String> getGetterDescription(PsiMethod getter) {
    final String name = getter.getName();
    if (getter instanceof GrGdkMethod) {
      return new Pair<>(null, "GDK method '" + name + "'");
    }
    else if (getter instanceof GrReflectedMethod) {
      getter = ((GrReflectedMethod)getter).getBaseMethod();
      final String info = PsiFormatUtil
        .formatMethod(getter, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                      PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_NAME);
      return Pair.create(((GrMethod)getter).getNameIdentifierGroovy(), "method " + info);
    }
    else if (getter instanceof GrMethod) {
      return Pair.create(((GrMethod)getter).getNameIdentifierGroovy(), "getter '" + name + "'");
    }
    else {
      final String info = PsiFormatUtil
        .formatMethod(getter, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
                      PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.SHOW_NAME);
      return new Pair<>(null, "method " + info);
    }
  }
}
