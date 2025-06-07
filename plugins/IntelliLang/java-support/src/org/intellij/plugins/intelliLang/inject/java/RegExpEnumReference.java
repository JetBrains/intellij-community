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
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
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

  RegExpEnumReference(PsiLiteralExpression expression, @NotNull String pattern) {
    super(expression);
    myPattern = pattern;
  }

  @Override
  public Object @NotNull [] getVariants() {
    final Set<String> values = getEnumValues();
    if (values == null || values.isEmpty()) {
      return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }
    return ContainerUtil.map2Array(values, s -> LookupElementBuilder.create(s).withIcon(PlatformIcons.ENUM_ICON));
  }

  @Override
  public boolean isSoft() {
    return true;
  }

  @Override
  public @Nullable PsiElement resolve() {
    final Set<String> values = getEnumValues();
    return values != null ? values.contains(getValue()) ? myValue : null : null;
  }

  private @Nullable Set<String> getEnumValues() {
    return RegExpUtil.getEnumValues(myValue.getProject(), myPattern);
  }
}
