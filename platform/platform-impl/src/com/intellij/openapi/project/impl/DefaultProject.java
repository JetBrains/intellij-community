// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl;

import com.intellij.openapi.components.ComponentConfig;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
final class DefaultProject extends ProjectImpl {
  private static final Logger LOG = Logger.getInstance(DefaultProject.class);
  private static final String TEMPLATE_PROJECT_NAME = "Default (Template) Project";

  DefaultProject(@NotNull String filePath) {
    super(filePath, TEMPLATE_PROJECT_NAME);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Created DefaultProject " + this, new Exception());
    }
  }

  @Override
  public boolean isDefault() {
    return true;
  }

  @Override
  public boolean isInitialized() {
    return true; // no startup activities, never opened
  }

  @Nullable
  @Override
  protected String activityNamePrefix() {
    // exclude from measurement because default project initialization is not a sequential activity
    // (so, complicates timeline because not applicable)
    // for now we don't measure default project initialization at all, because it takes only ~10 ms
    return null;
  }

  @Override
  protected boolean isComponentSuitable(@NotNull ComponentConfig componentConfig) {
    return super.isComponentSuitable(componentConfig) && componentConfig.isLoadForDefaultProject();
  }

  @Override
  public synchronized void dispose() {
    super.dispose();
    if (LOG.isDebugEnabled()) {
      LOG.debug("Disposed DefaultProject "+this);
    }
    ((ProjectManagerImpl)ProjectManager.getInstance()).updateTheOnlyProjectField();
  }
}
