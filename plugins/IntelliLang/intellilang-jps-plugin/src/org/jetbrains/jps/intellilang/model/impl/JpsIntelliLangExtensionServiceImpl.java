// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.intellilang.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.intellilang.model.JpsIntelliLangConfiguration;
import org.jetbrains.jps.intellilang.model.JpsIntelliLangExtensionService;
import org.jetbrains.jps.model.JpsGlobal;

/**
 * @author Eugene Zhuravlev
 */
public class JpsIntelliLangExtensionServiceImpl extends JpsIntelliLangExtensionService {
  @NotNull
  @Override
  public JpsIntelliLangConfiguration getConfiguration(@NotNull JpsGlobal global) {
    JpsIntelliLangConfiguration configuration = global.getContainer().getChild(JpsIntelliLangConfigurationImpl.ROLE);
    return configuration != null ? configuration : new JpsIntelliLangConfigurationImpl();
  }

  @Override
  public void setConfiguration(@NotNull JpsGlobal global, @NotNull JpsIntelliLangConfiguration config) {
    global.getContainer().setChild(JpsIntelliLangConfigurationImpl.ROLE, config);
  }
}