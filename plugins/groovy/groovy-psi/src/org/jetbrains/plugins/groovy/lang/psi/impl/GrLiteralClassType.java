// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import static com.intellij.openapi.util.RecursionManager.doPreventingRecursion;
import static com.intellij.openapi.util.text.StringUtil.getShortName;

public abstract class GrLiteralClassType extends PsiClassType {
  protected final GlobalSearchScope myScope;
  protected final JavaPsiFacade myFacade;
  private final GroovyPsiManager myGroovyPsiManager;
  private final Object myResolveResultGuardKey = new Object();

  public GrLiteralClassType(@NotNull LanguageLevel languageLevel, @NotNull GlobalSearchScope scope, @NotNull JavaPsiFacade facade) {
    super(languageLevel);
    myScope = scope;
    myFacade = facade;
    myGroovyPsiManager = GroovyPsiManager.getInstance(myFacade.getProject());
  }

  protected GrLiteralClassType(@NotNull LanguageLevel languageLevel, @NotNull PsiElement context) {
    super(languageLevel);
    myScope = context.getResolveScope();
    Project project = context.getProject();
    myFacade = JavaPsiFacade.getInstance(project);
    myGroovyPsiManager = GroovyPsiManager.getInstance(project);
  }

  protected abstract @NotNull String getJavaClassName();

  @Override
  public @NotNull ClassResolveResult resolveGenerics() {
    return new ClassResolveResult() {
      private final PsiClass myBaseClass = resolve();

      private final NotNullLazyValue<PsiSubstitutor> mySubstitutor = NotNullLazyValue.lazy(() -> {
        return inferSubstitutor(myBaseClass);
      });

      @Override
      public PsiClass getElement() {
        return myBaseClass;
      }

      @Override
      public @NotNull PsiSubstitutor getSubstitutor() {
        PsiSubstitutor substitutor = doPreventingRecursion(myResolveResultGuardKey, false, () -> mySubstitutor.getValue());
        return substitutor == null ? PsiSubstitutor.EMPTY : substitutor;
      }

      @Override
      public boolean isPackagePrefixPackageReference() {
        return false;
      }

      @Override
      public boolean isAccessible() {
        return true;
      }

      @Override
      public boolean isStaticsScopeCorrect() {
        return true;
      }

      @Override
      public @Nullable PsiElement getCurrentFileResolveScope() {
        return null;
      }

      @Override
      public boolean isValidResult() {
        return isStaticsScopeCorrect() && isAccessible();
      }
    };
  }

  private @NotNull PsiSubstitutor inferSubstitutor(@Nullable PsiClass myBaseClass) {
    if (myBaseClass != null) {
      final PsiType[] typeArgs = getParameters();
      final PsiTypeParameter[] typeParams = myBaseClass.getTypeParameters();
      if (typeParams.length == typeArgs.length) {
        return PsiSubstitutor.EMPTY.putAll(myBaseClass, typeArgs);
      }
      else {
        return PsiSubstitutor.EMPTY.putAll(myBaseClass, createArray(typeParams.length));
      }
    }
    else {
      return PsiSubstitutor.EMPTY;
    }
  }

  @Override
  public @NotNull String getClassName() {
    return getShortName(getJavaClassName());
  }

  @Override
  public @NotNull String getPresentableText() {
    String name = getClassName();
    final PsiType[] params = getParameters();
    if (params.length == 0 || params[0] == null) return name;

    Function<PsiType, String> f = psiType -> psiType == this ? getClassName() : psiType.getPresentableText();
    return name + "<" + StringUtil.join(params, f, ", ") + ">";
  }

  @Override
  public @NotNull String getCanonicalText() {
    String name = getJavaClassName();
    final PsiType[] params = getParameters();
    if (params.length == 0 || params[0] == null) return name;

    final Function<PsiType, String> f = psiType -> psiType == this ? getJavaClassName() : psiType.getCanonicalText();
    return name + "<" + StringUtil.join(params, f, ", ") + ">";
  }

  @Override
  public @NotNull LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }

  @Override
  public @Nullable PsiClass resolve() {
    return myFacade.findClass(getJavaClassName(), getResolveScope());
  }

  @Override
  public @NotNull PsiClassType rawType() {
    return myGroovyPsiManager.createTypeByFQClassName(getJavaClassName(), myScope);
  }

  @Override
  public boolean equalsToText(@NotNull @NonNls String text) {
    return text.equals(getJavaClassName());
  }

  @Override
  public @NotNull GlobalSearchScope getResolveScope() {
    return myScope;
  }

  protected static String getInternalCanonicalText(@Nullable PsiType type) {
    if (type == null) return CommonClassNames.JAVA_LANG_OBJECT;
    return doPreventingRecursion(type, false, () -> type.getInternalCanonicalText());
  }

  protected @NotNull PsiType getLeastUpperBound(PsiType... psiTypes) {
    PsiType result = null;
    final PsiManager manager = getPsiManager();
    for (final PsiType other : psiTypes) {
      result = TypesUtil.getLeastUpperBoundNullable(result, other, manager);
    }
    return result == null ? LazyFqnClassType
      .getLazyType(CommonClassNames.JAVA_LANG_OBJECT, getLanguageLevel(), getResolveScope(), myFacade) : result;
  }

  protected PsiManager getPsiManager() {
    return PsiManager.getInstance(myFacade.getProject());
  }
}
