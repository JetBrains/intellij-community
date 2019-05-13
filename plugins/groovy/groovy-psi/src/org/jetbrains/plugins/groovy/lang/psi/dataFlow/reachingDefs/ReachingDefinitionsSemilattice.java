/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.reachingDefs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;

import java.util.List;

/**
 * @author ven
 */
public class ReachingDefinitionsSemilattice implements Semilattice<DefinitionMap> {
  @NotNull
  @Override
  public DefinitionMap join(@NotNull List<? extends DefinitionMap> ins) {
    if (ins.isEmpty()) return new DefinitionMap();

    DefinitionMap result = new DefinitionMap();
    for (DefinitionMap map : ins) {
      result.merge(map);
    }

    return result;
  }

  @Override
  public boolean eq(@NotNull final DefinitionMap m1, @NotNull final DefinitionMap m2) {
    return m1.eq(m2);
  }
}
