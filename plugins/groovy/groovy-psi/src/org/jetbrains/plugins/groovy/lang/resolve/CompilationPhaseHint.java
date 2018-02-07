// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public interface CompilationPhaseHint {

  Key<CompilationPhaseHint> HINT_KEY = Key.create("groovy.compilation.phase");

  CompilationPhaseHint BEFORE_TRANSFORMATION = () -> Phase.TRANSFORMATION;

  enum Phase {
    CONVERSION,
    TRANSFORMATION
  }

  @NotNull
  Phase beforePhase();
}
