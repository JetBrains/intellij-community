// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult;

public interface GroovyOverloadResolver {

  /**
   * Compares two applicable resolve results in order to find more preferable.
   *
   * @return <ul>
   * <li>negative value if {@code left} is preferable</li>
   * <li>0 if none are preferable</li>
   * <li>positive if {@code right} is preferable</li>
   * </ul>
   */
  int compare(@NotNull GroovyMethodResult left, @NotNull GroovyMethodResult right);
}
