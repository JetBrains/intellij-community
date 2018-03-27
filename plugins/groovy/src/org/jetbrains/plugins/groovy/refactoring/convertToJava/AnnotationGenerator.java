// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import java.util.Collections;
import java.util.List;

/**
 * @author Medvedev Max
 */
public class AnnotationGenerator extends Generator {
  @NotNull
  private List<String> SKIPPED = Collections.singletonList("groovy.transform");
  private final StringBuilder builder;

  private final ExpressionContext context;
  private final ExpressionGenerator expressionGenerator;

  public AnnotationGenerator(StringBuilder builder, ExpressionContext context) {
    this.builder = builder;
    this.context = context.extend();
    expressionGenerator = new ExpressionGenerator(builder, context);
  }

  @Override
  public StringBuilder getBuilder() {
    return builder;
  }

  @Override
  public ExpressionContext getContext() {
    return context;
  }

  @Override
  public void visitExpression(@NotNull GrExpression expression) {
    expression.accept(expressionGenerator);
  }

  @Override
  public void visitAnnotationArrayInitializer(@NotNull GrAnnotationArrayInitializer arrayInitializer) {
    GrAnnotationMemberValue[] initializers = arrayInitializer.getInitializers();
    builder.append('{');
    for (GrAnnotationMemberValue initializer : initializers) {
      initializer.accept(this);
      builder.append(", ");
    }

    if (initializers.length > 0) {
      builder.delete(builder.length()-2, builder.length());
      //builder.removeFromTheEnd(2);
    }

    builder.append('}');
  }

  @Override
  public void visitAnnotation(@NotNull GrAnnotation annotation) {
    String qualifiedName = annotation.getQualifiedName();
    if (qualifiedName != null && SKIPPED.stream().anyMatch(qualifiedName::contains)) return;
    builder.append('@');
    GrCodeReferenceElement classReference = annotation.getClassReference();
    GenerationUtil.writeCodeReferenceElement(builder, classReference);
    GrAnnotationArgumentList parameterList = annotation.getParameterList();
    GrAnnotationNameValuePair[] attributes = parameterList.getAttributes();
    if (attributes.length == 0) return;

    builder.append('(');

    for (GrAnnotationNameValuePair attribute : attributes) {
      String name = attribute.getName();
      if (name != null) {
        builder.append(name);
        builder.append(" = ");
      }
      GrAnnotationMemberValue value = attribute.getValue();
      if (value != null) {
        value.accept(this);
      }
      builder.append(", ");
    }
    builder.delete(builder.length()-2, builder.length());
    //builder.removeFromTheEnd(2);
    builder.append(')');
  }


}
