// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;

public class AnnotationArgConverter {
  private static final Logger LOG = Logger.getInstance(AnnotationArgConverter.class);

  @Nullable
  public GrAnnotationMemberValue convert(PsiAnnotationMemberValue value) {
    final StringBuilder buffer = new StringBuilder();

    buffer.append("@A(");
    generateText(value, buffer);
    buffer.append(")");

    String text = buffer.toString();
    try {
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(value.getProject());
      return factory.createAnnotationFromText(text).getParameterList().getAttributes()[0].getValue();
    }
    catch (IncorrectOperationException | ArrayIndexOutOfBoundsException e) {
      LOG.error("Text: \"" + text + "\"", e);
      return null;
    }
  }

  private void generateText(PsiAnnotationMemberValue value, final @NlsSafe StringBuilder buffer) {
    value.accept(new JavaElementVisitor() {
      @Override
      public void visitAnnotation(PsiAnnotation annotation) {
        buffer.append("@");
        PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
        if (ref == null) return;

        PsiElement resolved = ref.resolve();

        if (resolved instanceof PsiClass && ((PsiClass)resolved).getQualifiedName() != null) {
          buffer.append(((PsiClass)resolved).getQualifiedName());
        }
        else {
          buffer.append(ref.getText());
        }

        PsiAnnotationParameterList parameterList = annotation.getParameterList();
        parameterList.accept(this);
      }

      @Override
      public void visitAnnotationParameterList(PsiAnnotationParameterList list) {
        PsiNameValuePair[] attributes = list.getAttributes();
        if (attributes.length > 0) {
          buffer.append('(');
          for (PsiNameValuePair attribute : attributes) {
            attribute.accept(this);
            buffer.append(',');
          }
          buffer.replace(buffer.length() - 1, buffer.length(), ")");
        }
      }

      @Override
      public void visitNameValuePair(PsiNameValuePair pair) {
        String name = pair.getName();
        PsiAnnotationMemberValue value = pair.getValue();

        if (name != null) {
          buffer.append(name);
          buffer.append('=');
        }

        if (value != null) {
          value.accept(this);
        }
      }

      @Override
      public void visitExpression(PsiExpression expression) {
        buffer.append(expression.getText());
      }

      @Override
      public void visitAnnotationArrayInitializer(PsiArrayInitializerMemberValue initializer) {
        PsiAnnotationMemberValue[] initializers = initializer.getInitializers();
        processInitializers(initializers);
      }

      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        PsiArrayInitializerExpression arrayInitializer = expression.getArrayInitializer();
        if (arrayInitializer == null) {
          super.visitNewExpression(expression);
        }
        else {
          PsiType type = expression.getType();
          if (type == null) {
            type = PsiType.getJavaLangObject(expression.getManager(), expression.getResolveScope()).createArrayType();
          }
          buffer.append('(');
          arrayInitializer.accept(this);
          buffer.append(" as ");
          buffer.append(type.getCanonicalText());
          buffer.append(")");
        }
      }

      @Override
      public void visitArrayInitializerExpression(PsiArrayInitializerExpression arrayInitializer) {
        processInitializers(arrayInitializer.getInitializers());
      }

      private void processInitializers(PsiAnnotationMemberValue[] initializers) {
        buffer.append('[');
        for (PsiAnnotationMemberValue initializer : initializers) {
          initializer.accept(this);
          buffer.append(',');
        }
        if (initializers.length > 0) {
          buffer.delete(buffer.length() - 1, buffer.length());
        }
        buffer.append(']');
      }
    });
  }
}
