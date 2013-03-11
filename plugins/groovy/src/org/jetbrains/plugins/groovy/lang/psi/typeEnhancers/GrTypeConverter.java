/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.typeEnhancers;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author peter
 */
public abstract class GrTypeConverter {
  public static final ExtensionPointName<GrTypeConverter> EP_NAME = ExtensionPointName.create("org.intellij.groovy.typeConverter");

  protected static boolean isMethodCallConversion(GroovyPsiElement context) {
    return PsiUtil.isInMethodCallContext(context);
  }

  public abstract boolean isAllowedInMethodCall();

  @Nullable
  public abstract Boolean isConvertible(@NotNull PsiType lType, @NotNull PsiType rType, @NotNull GroovyPsiElement context);

  protected static boolean isEnum(PsiType type) {
    if (type instanceof PsiClassType) {
      final PsiClass resolved = ((PsiClassType)type).resolve();
      return resolved != null && resolved.isEnum();
    }

    return false;
  }

  public static Boolean isConvertibleWithMethodCallConversion(@NotNull PsiType lType,
                                                               @NotNull PsiType rType,
                                                               @NotNull GroovyPsiElement context) {
    for (GrTypeConverter converter : EP_NAME.getExtensions()) {
      if (converter.isAllowedInMethodCall()) {
        Boolean result = converter.isConvertible(lType, rType, context);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }

  public static Boolean isConvertibleAll(@NotNull PsiType lType,
                                       @NotNull PsiType rType,
                                       @NotNull GroovyPsiElement context) {
    for (GrTypeConverter converter : EP_NAME.getExtensions()) {
      if (!converter.isAllowedInMethodCall()) {
        Boolean result = converter.isConvertible(lType, rType, context);
        if (result != null) {
          return result;
        }
      }
    }
    return null;
  }
}
