// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.config;

import com.intellij.framework.FrameworkTypeEx;
import com.intellij.framework.addSupport.FrameworkSupportInModuleConfigurable;
import com.intellij.framework.addSupport.FrameworkSupportInModuleProvider;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import org.jetbrains.annotations.NotNull;

public class GroovyFrameworkSupportProvider extends FrameworkSupportInModuleProvider {
  @Override
  public @NotNull FrameworkTypeEx getFrameworkType() {
    return FrameworkTypeEx.EP_NAME.findExtension(GroovyFrameworkType.class);
  }

  @Override
  public boolean isEnabledForModuleBuilder(@NotNull ModuleBuilder builder) {
    return super.isEnabledForModuleBuilder(builder) && !(builder instanceof GroovyAwareModuleBuilder);
  }

  @Override
  public boolean isSupportAlreadyAdded(@NotNull Module module, @NotNull FacetsProvider facetsProvider) {
    final String version = GroovyConfigUtils.getInstance().getSDKVersion(module);
    return version != null;
  }

  @Override
  public @NotNull FrameworkSupportInModuleConfigurable createConfigurable(final @NotNull FrameworkSupportModel model) {
    return new GroovySupportConfigurable();
  }

  @Override
  public boolean isEnabledForModuleType(@NotNull ModuleType<?> moduleType) {
    return GroovyFacetUtil.isAcceptableModuleType(moduleType);
  }
}
