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

package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;

/**
 * @author ven
 */
public class GrGdkMethodImpl extends LightMethodBuilder implements GrGdkMethod {
  private static final Key<CachedValue<GrGdkMethodImpl>> CACHED_STATIC = Key.create("Cached static gdk method");
  private static final Key<CachedValue<GrGdkMethodImpl>> CACHED_NON_STATIC = Key.create("Cached instance gdk method");
  private final PsiMethod myMethod;

  private GrGdkMethodImpl(PsiMethod method, boolean isStatic, @Nullable String originInfo) {
    super(method.getManager(), GroovyLanguage.INSTANCE, method.getName());
    myMethod = method;

    addModifier(PsiModifier.PUBLIC);
    if (isStatic) {
      addModifier(PsiModifier.STATIC);
    }

    final PsiParameter[] originalParameters = method.getParameterList().getParameters();
    for (int i = 1; i < originalParameters.length; i++) {
      addParameter(originalParameters[i]);
    }

    setMethodReturnType(method.getReturnType());
    setBaseIcon(JetgroovyIcons.Groovy.Method);
    setMethodKind("GrGdkMethod");

    if (originInfo != null) {
      setOriginInfo(originInfo);
    }
  }

  @Override
  @NotNull
  public PsiMethod getStaticMethod() {
    return myMethod;
  }

  @Override
  public boolean hasTypeParameters() {
    return myMethod.hasTypeParameters();
  }

  @Override
  @NotNull
  public PsiTypeParameter[] getTypeParameters() {
    return myMethod.getTypeParameters();
  }

  @Override
  public PsiTypeParameterList getTypeParameterList() {
    return myMethod.getTypeParameterList();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GrGdkMethodImpl)) return false;

    GrGdkMethodImpl that = (GrGdkMethodImpl)o;

    if (myMethod != null ? !myMethod.equals(that.myMethod) : that.myMethod != null) return false;
    if (hasModifierProperty(PsiModifier.STATIC) != that.hasModifierProperty(PsiModifier.STATIC)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myMethod.hashCode();
  }

  @NotNull
  public static GrGdkMethod createGdkMethod(@NotNull final PsiMethod original,
                                            final boolean isStatic,
                                            @Nullable final String originInfo) {
    final Key<CachedValue<GrGdkMethodImpl>> cachedValueKey = isStatic ? CACHED_STATIC : CACHED_NON_STATIC;
    CachedValue<GrGdkMethodImpl> cachedValue = original.getUserData(cachedValueKey);
    if (cachedValue == null) {
      cachedValue = CachedValuesManager.getManager(original.getProject()).createCachedValue(new CachedValueProvider<GrGdkMethodImpl>() {
        @Override
        public Result<GrGdkMethodImpl> compute() {
          return Result.create(new GrGdkMethodImpl(original, isStatic, originInfo),
                               PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
        }
      }, false);
      original.putUserData(cachedValueKey, cachedValue);
    }

    return cachedValue.getValue();
  }

  /*
   * Override LightElement.isValid() to avoid calling getNavigationElement()
   */
  @Override
  public boolean isValid() {
    return myMethod.isValid();
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    PsiElement navigationElement = myMethod.getNavigationElement();
    return navigationElement == null ? myMethod : navigationElement;
  }

  @Override
  public void setNavigationElement(@NotNull PsiElement navigationElement) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public PsiElement getPrototype() {
    return getStaticMethod();
  }
}
