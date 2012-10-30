package org.jetbrains.jps.appengine.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.service.JpsServiceManager;

/**
 * @author nik
 */
public abstract class JpsAppEngineExtensionService {
  public static JpsAppEngineExtensionService getInstance() {
    return JpsServiceManager.getInstance().getService(JpsAppEngineExtensionService.class);
  }

  @Nullable
  public abstract JpsAppEngineModuleExtension getExtension(@NotNull JpsModule module);
}
