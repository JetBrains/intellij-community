package org.jetbrains.jps.appengine.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.appengine.model.JpsAppEngineExtensionService;
import org.jetbrains.jps.appengine.model.JpsAppEngineModuleExtension;
import org.jetbrains.jps.model.module.JpsModule;

/**
 * @author nik
 */
public class JpsAppEngineExtensionServiceImpl extends JpsAppEngineExtensionService {
  @Nullable
  @Override
  public JpsAppEngineModuleExtension getExtension(@NotNull JpsModule module) {
    return module.getContainer().getChild(JpsAppEngineModuleExtensionImpl.ROLE);
  }
}
