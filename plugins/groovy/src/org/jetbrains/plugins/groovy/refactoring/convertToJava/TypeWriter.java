// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

/**
 * @author Medvedev Max
 */
public class TypeWriter extends PsiTypeVisitor<Object> {

  private final boolean acceptEllipsis;
  private final StringBuilder builder;
  private final ClassNameProvider classNameProvider;
  private final PsiElement context;

  public static void writeTypeForNew(@NotNull StringBuilder builder, @Nullable PsiType type, @NotNull PsiElement context) {

    //new Array[] cannot contain generics
    if (type instanceof PsiArrayType) {
      PsiType erased = TypeConversionUtil.erasure(type);
      if (erased != null) {
        type = erased;
      }
    }

    writeType(builder, type, context, new GeneratorClassNameProvider());
  }

  public static void writeType(@NotNull StringBuilder builder, @Nullable PsiType type, @NotNull PsiElement context) {
    writeType(builder, type, context, new GeneratorClassNameProvider());
  }

  public static void writeType(@NotNull final StringBuilder builder,
                               @Nullable PsiType type,
                               @NotNull final PsiElement context,
                               @NotNull final ClassNameProvider classNameProvider) {
    if (type == null || PsiType.NULL.equals(type)) {
      builder.append(CommonClassNames.JAVA_LANG_OBJECT);
      return;
    }

    if (type instanceof PsiPrimitiveType) {
      builder.append(type.getCanonicalText());
      return;
    }

    final boolean acceptEllipsis = isLastParameter(context);

    type.accept(new TypeWriter(builder, classNameProvider, acceptEllipsis, context));
  }

  private TypeWriter(@NotNull StringBuilder builder,
                     @NotNull ClassNameProvider classNameProvider,
                     boolean acceptEllipsis,
                     @NotNull PsiElement context) {
    this.acceptEllipsis = acceptEllipsis;
    this.builder = builder;
    this.classNameProvider = classNameProvider;
    this.context = context;
  }

  @Override
  public Object visitEllipsisType(@NotNull PsiEllipsisType ellipsisType) {
    final PsiType componentType = ellipsisType.getComponentType();
    componentType.accept(this);
    if (acceptEllipsis) {
      builder.append("...");
    }
    else {
      builder.append("[]");
    }
    return this;
  }

  @Override
  public Object visitPrimitiveType(@NotNull PsiPrimitiveType primitiveType) {
    if (classNameProvider.forStubs()) {
      builder.append(primitiveType.getCanonicalText());
      return this;
    }
    final PsiType boxed = TypesUtil.boxPrimitiveType(primitiveType, context.getManager(), context.getResolveScope());
    boxed.accept(this);
    return this;
  }

  @Override
  public Object visitArrayType(@NotNull PsiArrayType arrayType) {
    arrayType.getComponentType().accept(this);
    builder.append("[]");
    return this;
  }

  @Override
  public Object visitClassType(@NotNull PsiClassType classType) {
    final PsiType[] parameters = classType.getParameters();
    final PsiClass psiClass = classType.resolve();
    if (psiClass == null) {
      builder.append(classType.getClassName());
    }
    else if (psiClass instanceof GrAnonymousClassDefinition) {
      visitClassType(((GrAnonymousClassDefinition)psiClass).getBaseClassType());
    }
    else {
      final String qname = classNameProvider.getQualifiedClassName(psiClass, context);
      builder.append(qname);
    }
    GenerationUtil.writeTypeParameters(builder, parameters, context, classNameProvider);
    return this;
  }

  @Override
  public Object visitCapturedWildcardType(@NotNull PsiCapturedWildcardType capturedWildcardType) {
    capturedWildcardType.getWildcard().accept(this);
    return this;
  }

  @Override
  public Object visitWildcardType(@NotNull PsiWildcardType wildcardType) {
    builder.append('?');
    PsiType bound = wildcardType.getBound();
    if (bound == null) return this;
    if (wildcardType.isExtends()) {
      builder.append(" extends ");
    }
    else {
      builder.append(" super ");
    }
    bound.accept(this);
    return this;
  }

  @Override
  public Object visitDisjunctionType(@NotNull PsiDisjunctionType disjunctionType) {
    //it is not available in groovy source code
    throw new UnsupportedOperationException();
  }

  @Override
  public Object visitType(@NotNull PsiType type) {
    throw new UnsupportedOperationException();
  }

  private static boolean isLastParameter(@NotNull PsiElement context) {
    if (context instanceof PsiParameter) {
      final PsiElement scope = ((PsiParameter)context).getDeclarationScope();
      if (scope instanceof PsiMethod) {
        final PsiParameter[] parameters = ((PsiMethod)scope).getParameterList().getParameters();
        return parameters.length > 0 && parameters[parameters.length - 1] == context;
      }
    }
    return false;
  }

}
