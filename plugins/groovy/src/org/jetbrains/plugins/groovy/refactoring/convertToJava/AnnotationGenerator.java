/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

/**
 * @author Medvedev Max
 */
public class AnnotationGenerator extends Generator {
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
  public void visitExpression(GrExpression expression) {
    expression.accept(expressionGenerator);
  }

  @Override
  public void visitAnnotationArrayInitializer(GrAnnotationArrayInitializer arrayInitializer) {
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
  public void visitAnnotation(GrAnnotation annotation) {
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
