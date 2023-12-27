// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  @Override
  public JpsIntelliLangConfigurationImpl createCopy() {
    return new JpsIntelliLangConfigurationImpl();
  }

  @NotNull
  @Override
  public String getPatternAnnotationClass() {
    return myPatternAnnotationClassName;
  }

  @NotNull
  @Override
  public InstrumentationType getInstrumentationType() {
    return myInstrumentationType;
  }

  public void setPatternAnnotationClassName(@NotNull String patternAnnotationClassName) {
    myPatternAnnotationClassName = patternAnnotationClassName;
  }

  public void setInstrumentationType(@NotNull InstrumentationType instrumentationType) {
    myInstrumentationType = instrumentationType;
  }
}