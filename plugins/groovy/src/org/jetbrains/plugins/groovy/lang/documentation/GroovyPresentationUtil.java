/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.documentation;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.MethodSignature;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author ven
 */
public class GroovyPresentationUtil {
  private static final int CONSTRAINTS_NUMBER = 2;
  
  public static String getParameterPresentation(GrParameter parameter, PsiSubstitutor substitutor) {
    StringBuilder builder = new StringBuilder();
    PsiType type = parameter.getTypeGroovy();
    if (type != null) {
      type = substitutor.substitute(type);
      return builder.append(type.getPresentableText()).append(" ").append(parameter.getName()).toString();
    } else {
      builder.append(parameter.getName());
      final Set<String> structural = new LinkedHashSet<String>();
      ReferencesSearch.search(parameter).forEach(new Processor<PsiReference>() {
        public boolean process(PsiReference ref) {
          PsiElement parent = ref.getElement().getParent();
          if (parent instanceof GrReferenceExpression) {

            if (structural.size() >= CONSTRAINTS_NUMBER) { //handle too many constraints
              structural.add("...");
              return false;
            }

            StringBuilder builder1 = new StringBuilder();
            builder1.append(((GrReferenceElement) parent).getReferenceName());
            PsiType[] argTypes = PsiUtil.getArgumentTypes(parent, true);
            if (argTypes != null) {
              builder1.append("(");
              if (argTypes.length > 0) {
                builder1.append(argTypes.length);
                if (argTypes.length == 1) {
                  builder1.append(" arg");
                } else {
                  builder1.append(" args");
                }
              }
              builder1.append(")");
            }

            structural.add(builder1.toString());
          }

          return true;
        }
      });

      if (!structural.isEmpty()) {
        builder.append(".");
        String[] array = ArrayUtil.toStringArray(structural);
        if (array.length> 1) builder.append("[");
        for (int i = 0; i < array.length; i++) {
          if (i > 0) builder.append(", ");
          builder.append(array[i]);
        }
        if (array.length> 1) builder.append("]");
      }
      return builder.toString();
    }
  }

  public static String getSignaturePresentation(MethodSignature signature) {
    StringBuilder builder = new StringBuilder();
    builder.append(signature.getName()).append('(');
    PsiType[] types = signature.getParameterTypes();
    for (PsiType type : types) {
      builder.append(type.getPresentableText()).append(", ");
    }
    if (types.length > 0) builder.delete(builder.length() - 2, builder.length());
    builder.append(")");
    return builder.toString();
  }
}
