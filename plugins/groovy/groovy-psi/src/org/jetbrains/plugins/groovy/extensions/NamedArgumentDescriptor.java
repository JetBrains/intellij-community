/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.extensions;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.impl.NamedArgumentDescriptorImpl;
import org.jetbrains.plugins.groovy.extensions.impl.StringTypeCondition;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;

import static com.intellij.psi.CommonClassNames.*;
import static org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor.Priority.*;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE;

public interface NamedArgumentDescriptor {

  NamedArgumentDescriptor SIMPLE_ON_TOP = new NamedArgumentDescriptorImpl(ALWAYS_ON_TOP);
  NamedArgumentDescriptor SIMPLE_AS_LOCAL_VAR = new NamedArgumentDescriptorImpl(AS_LOCAL_VARIABLE);
  NamedArgumentDescriptor SIMPLE_NORMAL = new NamedArgumentDescriptorImpl(NORMAL);
  NamedArgumentDescriptor SIMPLE_UNLIKELY = new NamedArgumentDescriptorImpl(UNLIKELY);

  NamedArgumentDescriptor TYPE_STRING = new StringTypeCondition(JAVA_LANG_STRING);
  NamedArgumentDescriptor TYPE_CLOSURE = new StringTypeCondition(GROOVY_LANG_CLOSURE);
  NamedArgumentDescriptor TYPE_MAP = new StringTypeCondition(JAVA_UTIL_MAP);
  NamedArgumentDescriptor TYPE_LIST = new StringTypeCondition(JAVA_UTIL_LIST);
  NamedArgumentDescriptor TYPE_BOOL = new StringTypeCondition(JAVA_LANG_BOOLEAN);
  NamedArgumentDescriptor TYPE_CLASS = new StringTypeCondition(JAVA_LANG_CLASS);
  NamedArgumentDescriptor TYPE_INTEGER = new StringTypeCondition(JAVA_LANG_INTEGER);

  @NotNull
  Priority getPriority();

  default boolean checkType(@NotNull PsiType type, @NotNull GroovyPsiElement context) {
    return true;
  }

  @Nullable
  default PsiPolyVariantReference createReference(@NotNull GrArgumentLabel label) {
    return null;
  }

  @Nullable
  default PsiElement getNavigationElement() {
    return null;
  }

  @Nullable
  default LookupElement customizeLookupElement(@NotNull LookupElementBuilder lookupElement) {
    return null;
  }

  enum Priority {
    ALWAYS_ON_TOP,
    AS_LOCAL_VARIABLE,
    NORMAL,
    UNLIKELY
  }
}
