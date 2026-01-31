// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import static com.intellij.psi.CommonClassNames.JAVA_LANG_BOOLEAN;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_CLASS;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_LIST;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_MAP;
import static org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor.Priority.ALWAYS_ON_TOP;
import static org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor.Priority.AS_LOCAL_VARIABLE;
import static org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor.Priority.NORMAL;
import static org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor.Priority.UNLIKELY;
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

  default @Nullable PsiPolyVariantReference createReference(@NotNull GrArgumentLabel label) {
    return null;
  }

  default @Nullable PsiElement getNavigationElement() {
    return null;
  }

  default @Nullable LookupElement customizeLookupElement(@NotNull LookupElementBuilder lookupElement) {
    return null;
  }

  enum Priority {
    ALWAYS_ON_TOP,
    AS_LOCAL_VARIABLE,
    NORMAL,
    UNLIKELY
  }
}
