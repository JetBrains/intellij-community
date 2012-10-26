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

import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

/**
 * @author Medvedev Max
 */
public class TypeWriter extends PsiTypeVisitor<Object> {

  private boolean acceptEllipsis;
  private StringBuilder builder;
  private ClassNameProvider classNameProvider;
  private PsiElement context;

  public static void writeTypeForNew(StringBuilder builder, @Nullable PsiType type, PsiElement context) {

    //new Array[] cannot contain generics
    if (type instanceof PsiArrayType) {
      PsiType erased = TypeConversionUtil.erasure(type);
      if (erased != null) {
        type = erased;
      }
    }

    writeType(builder, type, context, new GeneratorClassNameProvider());
  }

  public static void writeType(StringBuilder builder, @Nullable PsiType type, PsiElement context) {
    writeType(builder, type, context, new GeneratorClassNameProvider());
  }

  public static void writeType(final StringBuilder builder,
                               @Nullable PsiType type,
                               final PsiElement context,
                               final ClassNameProvider classNameProvider) {
    if (type instanceof PsiPrimitiveType) {
      builder.append(type.getCanonicalText());
      return;
    }

    if (type == null) {
      builder.append(CommonClassNames.JAVA_LANG_OBJECT);
      return;
    }

    final boolean acceptEllipsis = isLastParameter(context);

    type.accept(new TypeWriter(builder, classNameProvider, acceptEllipsis, context));
  }

  private TypeWriter(StringBuilder builder, ClassNameProvider classNameProvider, boolean acceptEllipsis, PsiElement context) {
    this.acceptEllipsis = acceptEllipsis;
    this.builder = builder;
    this.classNameProvider = classNameProvider;
    this.context = context;
  }

  @Override
  public Object visitEllipsisType(PsiEllipsisType ellipsisType) {
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
  public Object visitPrimitiveType(PsiPrimitiveType primitiveType) {
    if (classNameProvider.forStubs()) {
      builder.append(primitiveType.getCanonicalText());
      return this;
    }
    final PsiType boxed = TypesUtil.boxPrimitiveType(primitiveType, context.getManager(), context.getResolveScope());
    boxed.accept(this);
    return this;
  }

  @Override
  public Object visitArrayType(PsiArrayType arrayType) {
    arrayType.getComponentType().accept(this);
    builder.append("[]");
    return this;
  }

  @Override
  public Object visitClassType(PsiClassType classType) {
    final PsiType[] parameters = classType.getParameters();
    final PsiClass psiClass = classType.resolve();
    if (psiClass == null) {
      builder.append(classType.getClassName());
    }
    else {
      final String qname = classNameProvider.getQualifiedClassName(psiClass, context);
      builder.append(qname);
    }
    GenerationUtil.writeTypeParameters(builder, parameters, context, classNameProvider);
    return this;
  }

  @Override
  public Object visitCapturedWildcardType(PsiCapturedWildcardType capturedWildcardType) {
    capturedWildcardType.getWildcard().accept(this);
    return this;
  }

  @Override
  public Object visitWildcardType(PsiWildcardType wildcardType) {
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
  public Object visitDisjunctionType(PsiDisjunctionType disjunctionType) {
    //it is not available in groovy source code
    throw new UnsupportedOperationException();
  }

  @Override
  public Object visitType(PsiType type) {
    throw new UnsupportedOperationException();
  }

  private static boolean isLastParameter(PsiElement context) {
    final PsiElement parent = context.getParent();
    return context instanceof PsiParameter &&
           parent instanceof PsiParameterList &&
           ((PsiParameterList)parent).getParameterIndex((PsiParameter)context) == ((PsiParameterList)parent).getParametersCount() - 1;
  }

}
