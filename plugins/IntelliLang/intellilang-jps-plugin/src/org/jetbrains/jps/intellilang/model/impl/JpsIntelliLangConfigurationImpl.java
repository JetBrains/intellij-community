// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.intellilang.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.intellilang.instrumentation.InstrumentationType;
import org.jetbrains.jps.intellilang.model.JpsIntelliLangConfiguration;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;

/**
 * @author Eugene Zhuravlev
 */
public class JpsIntelliLangConfigurationImpl extends JpsElementBase<JpsIntelliLangConfigurationImpl> implements JpsIntelliLangConfiguration {
  public static final JpsElementChildRole<JpsIntelliLangConfiguration> ROLE = JpsElementChildRoleBase.create("LangInjection");

  private String myPatternAnnotationClassName = "org.intellij.lang.annotations.Pattern";
  private InstrumentationType myInstrumentationType = InstrumentationType.ASSERT;

  @Override
  public @NotNull JpsIntelliLangConfigurationImpl createCopy() {
    return new JpsIntelliLangConfigurationImpl();
  }

  @Override
  public @NotNull String getPatternAnnotationClass() {
    return myPatternAnnotationClassName;
  }

  @Override
  public @NotNull InstrumentationType getInstrumentationType() {
    return myInstrumentationType;
  }

  public void setPatternAnnotationClassName(@NotNull String patternAnnotationClassName) {
    myPatternAnnotationClassName = patternAnnotationClassName;
  }

  public void setInstrumentationType(@NotNull InstrumentationType instrumentationType) {
    myInstrumentationType = instrumentationType;
  }
}