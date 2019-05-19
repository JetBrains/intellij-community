// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.intellilang.instrumentation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;

import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class PatternValidatorBuilderService extends BuilderService{
  @NotNull
  @Override
  public List<? extends ModuleLevelBuilder> createModuleLevelBuilders() {
    return Collections.singletonList(new PatternValidatorBuilder());
  }
}