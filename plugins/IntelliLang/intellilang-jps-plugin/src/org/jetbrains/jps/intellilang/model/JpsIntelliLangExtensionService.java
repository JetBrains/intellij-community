// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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