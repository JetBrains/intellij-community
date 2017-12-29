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
public class JpsIntelliLangConfigurationImpl extends JpsElementBase<JpsIntelliLangConfigurationImpl> implements
                                                                                             JpsIntelliLangConfiguration {
  public static final JpsElementChildRole<JpsIntelliLangConfiguration> ROLE = JpsElementChildRoleBase.create("IntelliLang");

  private String myPatternAnnotationClassName = "org.intellij.lang.annotations.Pattern";
  private InstrumentationType myInstrumentationType = InstrumentationType.ASSERT;

  @NotNull
  @Override
  public JpsIntelliLangConfigurationImpl createCopy() {
    return new JpsIntelliLangConfigurationImpl();
  }

  @Override
  public void applyChanges(@NotNull JpsIntelliLangConfigurationImpl modified) {
    myPatternAnnotationClassName = modified.myPatternAnnotationClassName;
    myInstrumentationType = modified.myInstrumentationType;
  }

  @Override
  public String getPatternAnnotationClass() {
    return myPatternAnnotationClassName;
  }

  @Override
  public InstrumentationType getInstrumentationType() {
    return myInstrumentationType;
  }

  public void setPatternAnnotationClassName(String patternAnnotationClassName) {
    myPatternAnnotationClassName = patternAnnotationClassName;
  }

  public void setInstrumentationType(InstrumentationType instrumentationType) {
    myInstrumentationType = instrumentationType;
  }
}
