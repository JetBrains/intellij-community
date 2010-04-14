/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.intelliLang.inject.java;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.Icons;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.plugins.intelliLang.util.RegExpUtil;
import org.intellij.plugins.intelliLang.util.StringLiteralReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Provides completion suggestions for enum-like regular expression patterns such as
 * <pre>@Pattern("abc|xyz|123")</pre>.
 */
final class RegExpEnumReference extends StringLiteralReference {
  private final String myPattern;

  public RegExpEnumReference(PsiLiteralExpression expression, @NotNull String pattern) {
    super(expression);
    myPattern = pattern;
  }

  @NotNull
  public Object[] getVariants() {
    final Set<String> values = getEnumValues();
    if (values == null || values.size() == 0) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    return ContainerUtil.map2Array(values, new Function<String, Object>() {
      public Object fun(String s) {
        return LookupElementBuilder.create(s).setIcon(Icons.ENUM_ICON);
      }
    });
  }

  public boolean isSoft() {
    return true;
  }

  @Nullable
  public PsiElement resolve() {
    final Set<String> values = getEnumValues();
    return values != null ? values.contains(getValue()) ? myValue : null : null;
  }

  @Nullable
  private Set<String> getEnumValues() {
    return RegExpUtil.getEnumValues(myValue.getProject(), myPattern);
  }
}
