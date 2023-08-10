// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.codeInsight.daemon.impl.quickfix.ClassKind;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;

import java.util.function.Supplier;

public enum GrCreateClassKind implements ClassKind {
  CLASS     (GroovyBundle.messagePointer("groovy.term.class")),
  INTERFACE (GroovyBundle.messagePointer("groovy.term.interface")),
  TRAIT     (GroovyBundle.messagePointer("groovy.term.trait")),
  ENUM      (GroovyBundle.messagePointer("groovy.term.enum")),
  ANNOTATION(GroovyBundle.messagePointer("groovy.term.annotation")),
  RECORD    (GroovyBundle.messagePointer("groovy.term.record"));

  private final Supplier<@Nls @NotNull String> myDescription;

  GrCreateClassKind(final Supplier<String> description) {
    myDescription = description;
  }

  @Override
  @Nls
  public String getDescription() {
    return myDescription.get();
  }
}
