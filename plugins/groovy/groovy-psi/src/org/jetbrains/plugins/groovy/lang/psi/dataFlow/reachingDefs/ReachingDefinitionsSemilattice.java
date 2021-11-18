// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;

import java.util.List;

/**
 * @author ven
 */
public class ReachingDefinitionsSemilattice implements Semilattice<DefinitionMap> {

  private final DefinitionMap NEUTRAL = new DefinitionMap();

  @Override
  @NotNull
  public DefinitionMap initial() {
    return NEUTRAL;
  }

  @NotNull
  @Override
  public DefinitionMap join(@NotNull List<? extends DefinitionMap> ins) {
    DefinitionMap result = new DefinitionMap();
    for (DefinitionMap map : ins) {
      result.mergeFrom(map);
    }

    return result;
  }
}
