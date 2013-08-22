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

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.isClassType;
import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.unboxPrimitiveTypeWrapper;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_GSTRING;

/**
 * Created by Max Medvedev on 8/15/13
 */
public class GrStringConverter extends GrTypeConverter {
  @Override
  public boolean isAllowedInMethodCall() {
    return false;
  }

  @Nullable
  @Override
  public Boolean isConvertible(@NotNull PsiType lType, @NotNull PsiType rType, @NotNull GroovyPsiElement context) {
    if (isClassType(lType, JAVA_LANG_STRING)) {
      return Boolean.TRUE;
    }

    if (unboxPrimitiveTypeWrapper(lType) == PsiType.CHAR &&
        (isClassType(rType, JAVA_LANG_STRING) || isClassType(rType, GROOVY_LANG_GSTRING))) {
      return Boolean.TRUE;
    }

    return null;
  }
}
