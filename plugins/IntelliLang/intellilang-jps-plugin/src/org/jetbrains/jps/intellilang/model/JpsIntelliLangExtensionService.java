package org.jetbrains.jps.intellilang.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.service.JpsServiceManager;

/**
 * @author Eugene Zhuravlev
 */
public abstract class JpsIntelliLangExtensionService {
  public static JpsIntelliLangExtensionService getInstance() {
    return JpsServiceManager.getInstance().getService(JpsIntelliLangExtensionService.class);
  }

  @NotNull
  public abstract JpsIntelliLangConfiguration getConfiguration(@NotNull JpsGlobal project);

  public abstract void setConfiguration(@NotNull JpsGlobal project, @NotNull JpsIntelliLangConfiguration extension);
}
