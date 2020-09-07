// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental.groovy;

import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public interface GroovycContinuation {

  @NotNull GroovyCompilerResult continueCompilation() throws Exception;

  void buildAborted();
}
