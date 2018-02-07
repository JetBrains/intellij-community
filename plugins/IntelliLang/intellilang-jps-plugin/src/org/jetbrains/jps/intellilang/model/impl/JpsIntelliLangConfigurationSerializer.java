package org.jetbrains.jps.intellilang.model.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.intellilang.instrumentation.InstrumentationType;
import org.jetbrains.jps.intellilang.model.JpsIntelliLangExtensionService;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.serialization.JpsGlobalExtensionSerializer;

/**
 * @author Eugene Zhuravlev
 */
public class JpsIntelliLangConfigurationSerializer extends JpsGlobalExtensionSerializer {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.intellilang.model.impl.JpsIntelliLangConfigurationSerializer");
  private static final String INSTRUMENTATION_TYPE_NAME = "INSTRUMENTATION";
  private static final String PATTERN_ANNOTATION_NAME = "PATTERN_ANNOTATION";

  public JpsIntelliLangConfigurationSerializer() {
    super("IntelliLang.xml", "LanguageInjectionConfiguration");
  }

  @Override
  public void loadExtension(@NotNull JpsGlobal global, @NotNull Element componentTag) {
    final JpsIntelliLangConfigurationImpl configuration = new JpsIntelliLangConfigurationImpl();

    final String annotationName = JDOMExternalizerUtil.readField(componentTag, PATTERN_ANNOTATION_NAME);
    if (annotationName != null) {
      configuration.setPatternAnnotationClassName(annotationName);
    }

    final String instrumentationType = JDOMExternalizerUtil.readField(componentTag, INSTRUMENTATION_TYPE_NAME);
    if (instrumentationType != null) {
      try {
        final InstrumentationType type = InstrumentationType.valueOf(instrumentationType);
        configuration.setInstrumentationType(type);
      }
      catch (IllegalArgumentException ignored) {
        LOG.info(ignored);
      }
    }

    JpsIntelliLangExtensionService.getInstance().setConfiguration(global, configuration);
  }

  @Override
  public void loadExtensionWithDefaultSettings(@NotNull JpsGlobal global) {
    JpsIntelliLangExtensionService.getInstance().setConfiguration(global, new JpsIntelliLangConfigurationImpl());
  }

  @Override
  public void saveExtension(@NotNull JpsGlobal global, @NotNull Element componentTag) {
  }
}
