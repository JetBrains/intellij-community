// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.openapi.util.Key;
import com.intellij.psi.scope.ElementClassHint.DeclarationKind;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum GroovyResolveKind {

  VARIABLE(DeclarationKind.VARIABLE),
  BINDING(DeclarationKind.VARIABLE),
  ENUM_CONST(DeclarationKind.ENUM_CONST),
  METHOD(DeclarationKind.METHOD),
  FIELD(DeclarationKind.FIELD),
  PROPERTY(DeclarationKind.METHOD, DeclarationKind.FIELD),
  TYPE_PARAMETER(DeclarationKind.CLASS),
  CLASS(DeclarationKind.CLASS),
  PACKAGE(DeclarationKind.PACKAGE);

  public final Set<DeclarationKind> declarationKinds;

  GroovyResolveKind(DeclarationKind kind, DeclarationKind... kinds) {
    declarationKinds = Collections.unmodifiableSet(EnumSet.of(kind, kinds));
  }

  public interface Hint {
    boolean shouldProcess(@NotNull GroovyResolveKind kind);
  }

  public static final Key<Hint> HINT_KEY = Key.create("groovy.resolve.kind.hint");
}
