// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.intellilang.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.intellilang.instrumentation.InstrumentationType;
import org.jetbrains.jps.model.JpsElement;

/**
 * @author Eugene Zhuravlev
 */
public interface JpsIntelliLangConfiguration extends JpsElement {
  @NotNull String getPatternAnnotationClass();

  @NotNull InstrumentationType getInstrumentationType();
}