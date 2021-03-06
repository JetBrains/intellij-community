// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.patterns;

import com.intellij.patterns.*;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.literals.GrLiteralImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;

public class GroovyPatterns extends PsiJavaPatterns {

  public static GroovyElementPattern groovyElement() {
    return new GroovyElementPattern.Capture<>(GroovyPsiElement.class);
  }

  public static GroovyBinaryExpressionPattern groovyBinaryExpression() {
    return new GroovyBinaryExpressionPattern();
  }

  public static GroovyAssignmentExpressionPattern groovyAssignmentExpression() {
    return new GroovyAssignmentExpressionPattern();
  }

  public static GroovyElementPattern.Capture<GrLiteral> groovyLiteralExpression() {
    return groovyLiteralExpression(null);
  }

  public static GroovyElementPattern.Capture<GrLiteral> groovyLiteralExpression(@Nullable final ElementPattern value) {
    return new GroovyElementPattern.Capture<>(new InitialPatternCondition<>(GrLiteral.class) {
      @Override
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        return o instanceof GrLiteral && (value == null || value.accepts(((GrLiteral)o).getValue(), context));
      }
    });
  }

  public static PsiMethodPattern grLightMethod(@NotNull final Object key) {
    return PsiJavaPatterns.psiMethod().with(new PatternCondition<>("GrLightMethodBuilder") {
      @Override
      public boolean accepts(@NotNull PsiMethod method, ProcessingContext context) {
        return GrLightMethodBuilder.checkKind(method, key);
      }
    });
  }

  public static GroovyElementPattern.Capture<GroovyPsiElement> rightOfAssignment(final ElementPattern<? extends GroovyPsiElement> value,
                                                                                 final GroovyAssignmentExpressionPattern assignment) {
    return new GroovyElementPattern.Capture<>(new InitialPatternCondition<>(GroovyPsiElement.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        if (!(o instanceof GroovyPsiElement)) return false;
        PsiElement parent = ((GroovyPsiElement)o).getParent();
        if (!(parent instanceof GrAssignmentExpression)) return false;
        if (((GrAssignmentExpression)parent).getRValue() != o) return false;
        return assignment.accepts(parent, context) && value.accepts(o, context);
      }
    });
  }

  public static GroovyElementPattern.Capture<GrLiteralImpl> stringLiteral() {
    return new GroovyElementPattern.Capture<>(new InitialPatternCondition<>(GrLiteralImpl.class) {
      @Override
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        if (!(o instanceof GrLiteralImpl)) return false;
        return ((GrLiteralImpl)o).isStringLiteral();
      }
    });
  }

  public static GroovyElementPattern.Capture<GrLiteralImpl> namedArgumentStringLiteral() {
    return stringLiteral().withParent(namedArgument());
  }

  public static GroovyNamedArgumentPattern namedArgument() {
    return new GroovyNamedArgumentPattern();
  }

  public static GroovyElementPattern.Capture<GrArgumentLabel> namedArgumentLabel(@Nullable final ElementPattern<String> namePattern) {
    return new GroovyElementPattern.Capture<>(new InitialPatternCondition<>(GrArgumentLabel.class) {
      @Override
      public boolean accepts(@Nullable final Object o, final ProcessingContext context) {
        if (o instanceof GrArgumentLabel) {
          PsiElement nameElement = ((GrArgumentLabel)o).getNameElement();
          if (nameElement instanceof LeafPsiElement) {
            IElementType elementType = ((LeafPsiElement)nameElement).getElementType();
            if (elementType == GroovyTokenTypes.mIDENT ||
                CommonClassNames.JAVA_LANG_STRING.equals(TypesUtil.getBoxedTypeName(elementType))) {
              return namePattern == null || namePattern.accepts(((GrArgumentLabel)o).getName());
            }
          }
        }
        return false;
      }
    });
  }

  public static GroovyMethodCallPattern methodCall(final ElementPattern<String> names, final String className) {
    return GroovyMethodCallPattern.INSTANCE.withMethodName(names).withMethod(
      psiMethod().with(new PatternCondition<>("psiMethodClassNameCondition") {
        @Override
        public boolean accepts(@NotNull PsiMethod psiMethod, ProcessingContext context) {
          PsiClass containingClass = psiMethod.getContainingClass();
          if (containingClass != null) {
            if (InheritanceUtil.isInheritor(containingClass, className)) {
              return true;
            }
          }
          return false;
        }
      })
    );
  }

  public static GroovyMethodCallPattern methodCall() {
    return GroovyMethodCallPattern.INSTANCE;
  }

  public static PsiFilePattern.Capture<GroovyFile> groovyScript() {
    return new PsiFilePattern.Capture<>(new InitialPatternCondition<>(GroovyFile.class) {
      @Override
      public boolean accepts(@Nullable Object o, ProcessingContext context) {
        return o instanceof GroovyFileBase && ((GroovyFileBase)o).isScript();
      }
    });
  }

  public static GroovyFieldPattern grField() {
    return new GroovyFieldPattern();
  }
}
