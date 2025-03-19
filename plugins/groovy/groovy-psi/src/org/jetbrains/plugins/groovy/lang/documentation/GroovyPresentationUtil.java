// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.documentation;

import com.intellij.codeInsight.javadoc.JavaDocInfoGeneratorFactory;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.lang.documentation.QuickDocHighlightingHelper;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.MethodSignature;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighter;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.transformations.impl.namedVariant.NamedParamData;
import org.jetbrains.plugins.groovy.transformations.impl.namedVariant.NamedParamsUtil;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GroovyPresentationUtil {
  private static final int CONSTRAINTS_NUMBER = 2;

  public static void appendParameterPresentation(
    GrParameter parameter,
    PsiSubstitutor substitutor,
    TypePresentation typePresentation,
    StringBuilder builder,
    boolean doHighlighting
  ) {
    if (presentNamedParameters(builder, parameter, doHighlighting)) return;

    for (PsiAnnotation annotation : parameter.getModifierList().getAnnotations()) {
      appendStyledSpan(doHighlighting, builder, GroovySyntaxHighlighter.ANNOTATION, annotation.getText());
      builder.append(' ');
    }

    PsiType type = parameter.getTypeGroovy();
    type = substitutor.substitute(type);

    if (typePresentation == TypePresentation.LINK) {
      if (type != null) {
        StringBuilder typeBuilder = new StringBuilder();
        JavaDocInfoGeneratorFactory.create(parameter.getProject(), null).generateType(typeBuilder, type, parameter);
        if (doHighlighting) {
          builder.append(typeBuilder);
        }
        else {
          builder.append(StringUtil.removeHtmlTags(typeBuilder.toString(), true));
        }
      }
      else {
        appendStyledSpan(doHighlighting, builder, GroovySyntaxHighlighter.KEYWORD, GrModifier.DEF);
      }
      builder.append(' ');
      appendStyledSpan(doHighlighting, builder, GroovySyntaxHighlighter.PARAMETER, parameter.getName());
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
          builder1.append(((GrReferenceElement<?>)parent).getReferenceName());
          PsiType[] argTypes = PsiUtil.getArgumentTypes(parent, true);
          if (argTypes != null) {
            builder1.append("(");
            builder1.append(GroovyBundle.message("parameter.hint.number.of.arguments", argTypes.length));
            builder1.append(')');
          }

          structural.add(builder1.toString());
        }

        return true;
      });

      if (!structural.isEmpty()) {
        builder.append(".");
        String[] array = ArrayUtilRt.toStringArray(structural);
        if (array.length > 1) builder.append("[");
        for (int i = 0; i < array.length; i++) {
          if (i > 0) builder.append(", ");
          builder.append(array[i]);
        }
        if (array.length > 1) builder.append("]");
      }
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

  private static @NotNull StringBuilder appendStyledSpan(
    boolean doHighlighting,
    @NotNull StringBuilder buffer,
    @NotNull TextAttributesKey attributesKey,
    @Nullable String value
  ) {
    if (doHighlighting) {
      QuickDocHighlightingHelper.appendStyledFragment(buffer, value, attributesKey);
    }
    else {
      buffer.append(value);
    }
    return buffer;
  }

  private static boolean presentNamedParameters(@NotNull StringBuilder buffer, @NotNull GrParameter parameter, boolean doHighlighting) {
    List<NamedParamData> pairs = NamedParamsUtil.collectNamedParams(parameter);
    for (int i = 0; i < pairs.size(); i++) {
      NamedParamData namedParam = pairs.get(i);
      appendStyledSpan(doHighlighting, buffer, GroovySyntaxHighlighter.PARAMETER, namedParam.getName());
      appendStyledSpan(doHighlighting, buffer, JavaHighlightingColors.OPERATION_SIGN, ": ");
      appendStyledSpan(doHighlighting, buffer, GroovySyntaxHighlighter.CLASS_REFERENCE, namedParam.getType().getPresentableText());
      if (i != pairs.size() - 1) {
        appendStyledSpan(doHighlighting, buffer, JavaHighlightingColors.COMMA, ", ");
      }
    }
    return !pairs.isEmpty();
  }
}
