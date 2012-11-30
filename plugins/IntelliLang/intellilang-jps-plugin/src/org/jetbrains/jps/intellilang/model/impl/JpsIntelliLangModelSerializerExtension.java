package org.jetbrains.jps.intellilang.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.JpsGlobalExtensionSerializer;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;

import java.util.Arrays;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: 11/29/12
 */
public class JpsIntelliLangModelSerializerExtension extends JpsModelSerializerExtension{
  @NotNull
  @Override
  public List<? extends JpsGlobalExtensionSerializer> getGlobalExtensionSerializers() {
    return Arrays.asList(new JpsIntelliLangConfigurationSerializer());
  }
}
