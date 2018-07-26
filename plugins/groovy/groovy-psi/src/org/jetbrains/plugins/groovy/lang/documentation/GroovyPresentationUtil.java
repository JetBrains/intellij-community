// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.documentation;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.MethodSignature;
import com.intellij.util.ArrayUtil;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.transformations.impl.namedVariant.NamedParamsUtil;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author ven
 */
public class GroovyPresentationUtil {
  private static final int CONSTRAINTS_NUMBER = 2;

  public static void appendParameterPresentation(GrParameter parameter,
                                                 PsiSubstitutor substitutor,
                                                 TypePresentation typePresentation,
                                                 StringBuilder builder) {
    if (presentNamedParameters(builder, parameter)) return;

    for (PsiAnnotation annotation : parameter.getModifierList().getAnnotations()) {
      builder.append(annotation.getText()).append(' ');
    }

    PsiType type = parameter.getTypeGroovy();
    type = substitutor.substitute(type);

    if (typePresentation == TypePresentation.LINK) {
      PsiImplUtil.appendTypeString(builder, type, parameter);
      builder.append(' ').append(parameter.getName());
      return;
    }

    if (type != null) {
      if (typePresentation == TypePresentation.PRESENTABLE) {
        builder.append(type.getPresentableText()).append(' ').append(parameter.getName());
      }
      else if (typePresentation == TypePresentation.CANONICAL) {
        builder.append(type.getCanonicalText()).append(' ').append(parameter.getName());
      }
    }
    else {
      builder.append(parameter.getName());
      final Set<String> structural = Collections.synchronizedSet(new LinkedHashSet<>());
      ReferencesSearch.search(parameter, parameter.getUseScope()).forEach(ref -> {
        PsiElement parent = ref.getElement().getParent();
        if (parent instanceof GrReferenceExpression) {

          if (structural.size() >= CONSTRAINTS_NUMBER) { //handle too many constraints
            structural.add("...");
            return false;
          }

          StringBuilder builder1 = new StringBuilder();
          builder1.append(((GrReferenceElement)parent).getReferenceName());
          PsiType[] argTypes = PsiUtil.getArgumentTypes(parent, true);
          if (argTypes != null) {
            builder1.append("(");
            if (argTypes.length > 0) {
              builder1.append(argTypes.length);
              if (argTypes.length == 1) {
                builder1.append(" arg");
              }
              else {
                builder1.append(" args");
              }
            }
            builder1.append(')');
          }

          structural.add(builder1.toString());
        }

        return true;
      });

      if (!structural.isEmpty()) {
        builder.append(".");
        String[] array = ArrayUtil.toStringArray(structural);
        if (array.length > 1) builder.append("[");
        for (int i = 0; i < array.length; i++) {
          if (i > 0) builder.append(", ");
          builder.append(array[i]);
        }
        if (array.length > 1) builder.append("]");
      }
    }
  }

  private static boolean presentNamedParameters(@NotNull StringBuilder buffer, @NotNull GrParameter parameter) {
    List<Pair<String, PsiType>> pairs = NamedParamsUtil.collectNamedParams(parameter);
    StringUtil.join(pairs, pair -> pair.getFirst() + ": " + pair.getSecond().getPresentableText(), ", ", buffer);
    return !pairs.isEmpty();
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
