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
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_BOOLEAN;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_CLASS;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_GSTRING;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.JAVA_LANG_CHAR_SEQUENCE;

/**
 * @author Max Medvedev
 */
public class GrStringTypeConverter extends GrTypeConverter {
  @Override
  public boolean isAllowedInMethodCall() {
    return false;
  }

  @Override
  public Boolean isConvertible(@NotNull PsiType lType, @NotNull PsiType rType, @NotNull GroovyPsiElement context) {
    if (!GroovyConfigUtils.getInstance().isVersionAtLeast(context, GroovyConfigUtils.GROOVY1_8)) return null;
    if (!(InheritanceUtil.isInheritor(rType, JAVA_LANG_CHAR_SEQUENCE) || InheritanceUtil.isInheritor(rType, GROOVY_LANG_GSTRING))) {
      return null;
    }

    if (lType == PsiType.BOOLEAN || TypesUtil.resolvesTo(lType, JAVA_LANG_BOOLEAN)) return true;
    if (TypesUtil.resolvesTo(lType, JAVA_LANG_CLASS)) return true;
    if (isEnum(lType)) return true;

    return null;
  }
}
