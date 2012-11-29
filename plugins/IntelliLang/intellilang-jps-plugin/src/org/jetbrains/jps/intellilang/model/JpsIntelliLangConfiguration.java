package org.jetbrains.jps.intellilang.model;

import org.jetbrains.jps.intellilang.instrumentation.InstrumentationType;
import org.jetbrains.jps.model.JpsElement;

/**
 * @author Eugene Zhuravlev
 *         Date: 11/29/12
 */
public interface JpsIntelliLangConfiguration extends JpsElement {
  String getPatternAnnotationClass();

  InstrumentationType getInstrumentationType();
}
