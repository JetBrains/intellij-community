// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
 * @author Maxim.Medvedev
 */
public class GrRangeType extends GrLiteralClassType {
  private final @Nullable PsiType myLeft;
  private final @Nullable PsiType myRight;
  private final PsiType myIterationType;
  private final String myQualifiedName;

  private final PsiType[] myParameters;

  public GrRangeType(@NotNull LanguageLevel languageLevel,
                     GlobalSearchScope scope,
                     JavaPsiFacade facade,
                     @Nullable PsiType left,
                     @Nullable PsiType right) {
    super(languageLevel, scope, facade);
    myLeft = left;
    myRight = right;
    myIterationType = TypesUtil
      .boxPrimitiveType(TypesUtil.getLeastUpperBoundNullable(myLeft, myRight, getPsiManager()), getPsiManager(), scope);
    if (PsiTypes.intType().equals(TypesUtil.unboxPrimitiveTypeWrapper(myIterationType))) {
      myQualifiedName = GroovyCommonClassNames.GROOVY_LANG_INT_RANGE;
    }
    else {
      myQualifiedName = GroovyCommonClassNames.GROOVY_LANG_RANGE;
    }

    myParameters = inferParameters();
  }

  public GrRangeType(GlobalSearchScope scope, JavaPsiFacade facade, @Nullable PsiType left, @Nullable PsiType right) {
    this(LanguageLevel.JDK_1_5, scope, facade, left, right);
  }

  @Override
  protected @NotNull String getJavaClassName() {
    return myQualifiedName;
  }

  @Override
  public PsiType @NotNull [] getParameters() {
    return myParameters;
  }

  private PsiType[] inferParameters() {
    if (myIterationType == null) return EMPTY_ARRAY;

    PsiClass resolved = resolve();
    if (resolved == null || resolved.getTypeParameters().length == 0) return EMPTY_ARRAY;

    return new PsiType[]{myIterationType};
  }

  @Override
  public @NotNull PsiClassType setLanguageLevel(@NotNull LanguageLevel languageLevel) {
    return new GrRangeType(languageLevel, myScope, myFacade, myLeft, myRight);
  }

  @Override
  public @NotNull String getInternalCanonicalText() {
    return "[" +
           (myLeft == null ? JavaKeywords.NULL : myLeft.getInternalCanonicalText()) +
           ".." +
           (myRight == null ? JavaKeywords.NULL : myRight.getInternalCanonicalText()) +
           "]";
  }

  @Override
  public boolean isValid() {
    return (myLeft == null || myLeft.isValid()) && (myRight == null || myRight.isValid());
  }

  public @Nullable PsiType getIterationType() {
    return myIterationType;
  }

  public @Nullable PsiType getLeft() {
    return myLeft;
  }

  public @Nullable PsiType getRight() {
    return myRight;
  }
}
