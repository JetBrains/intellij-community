// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;

import java.util.List;

import static org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs.DefinitionMap.NEUTRAL;

/**
 * @author ven
 */
public class ReachingDefinitionsSemilattice implements Semilattice<DefinitionMap> {

  @Override
  @NotNull
  public DefinitionMap initial() {
    return NEUTRAL;
  }

  @NotNull
  @Override
  public DefinitionMap join(@NotNull List<? extends DefinitionMap> ins) {
    if (ins.size() == 0) {
      return NEUTRAL;
    }
    DefinitionMap result = ins.get(0);
    for (int i = 1; i < ins.size(); i++) {
      result = result.withMerged(ins.get(i));
    }

    return result;
  }
}
