/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;

/**
 * Created by Max Medvedev on 8/19/13
 */
public class AnnotationArgConverter {
  @Nullable
  public GrAnnotationMemberValue convert(PsiAnnotationMemberValue value) {
    final StringBuilder buffer = new StringBuilder();

    buffer.append("@A(");
    generateText(value, buffer);
    buffer.append(")");

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(value.getProject());
    try {
      return factory.createAnnotationFromText(buffer.toString()).getParameterList().getAttributes()[0].getValue();
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  private void generateText(PsiAnnotationMemberValue value, final StringBuilder buffer) {
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
