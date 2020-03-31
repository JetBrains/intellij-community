// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.ElementClassHint.DeclarationKind;
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument;

import java.util.EnumSet;

import static com.intellij.psi.scope.ElementClassHint.DeclarationKind.*;

/**
 * @author ven
 */
public interface ClassHint {
  Key<PsiElement> RESOLVE_CONTEXT = Key.create("RESOLVE_CONTEXT");
  Key<Argument> RECEIVER = Key.create("groovy.resolve.receiver");
  Key<Boolean> STATIC_CONTEXT = Key.create("groovy.resolve.static.context");

  ElementClassHint EMPTY = kind -> false;

  EnumSet<DeclarationKind> RESOLVE_KINDS_CLASS = EnumSet.of(CLASS);
  EnumSet<DeclarationKind> RESOLVE_KINDS_METHOD = EnumSet.of(METHOD);
  EnumSet<DeclarationKind> RESOLVE_KINDS_PROPERTY = EnumSet.of(VARIABLE, FIELD, ENUM_CONST);
  EnumSet<DeclarationKind> RESOLVE_KINDS_METHOD_PROPERTY = EnumSet.of(METHOD, VARIABLE, FIELD, ENUM_CONST);
}
