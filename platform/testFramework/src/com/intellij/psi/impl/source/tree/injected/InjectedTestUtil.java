// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.mock.MockApplication;
import com.intellij.mock.MockDumbService;
import com.intellij.mock.MockProjectEx;
import com.intellij.openapi.extensions.BaseExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.LanguageInjector;
import org.jetbrains.annotations.NotNull;

public final class InjectedTestUtil {
  public static void registerMockInjectedLanguageManager(@NotNull MockApplication application,
                                                         @NotNull MockProjectEx project,
                                                         @NotNull PluginDescriptor pluginDescriptor) {
    registerExtensionPoint(project.getExtensionArea(), MultiHostInjector.MULTIHOST_INJECTOR_EP_NAME, MultiHostInjector.class,
                           pluginDescriptor);

    registerExtensionPoint(application.getExtensionArea(), LanguageInjector.EXTENSION_POINT_NAME, LanguageInjector.class, pluginDescriptor);
    project.registerService(DumbService.class, new MockDumbService(project));
    application.registerService(InjectedEditorWindowTracker.class, new InjectedEditorWindowTrackerImpl());
    project.registerService(InjectedLanguageManager.class, new InjectedLanguageManagerImpl(project));
  }
  private static <T> ExtensionPointImpl<T> registerExtensionPoint(@NotNull ExtensionsAreaImpl extensionArea,
                                                                  @NotNull BaseExtensionPointName<T> extensionPointName,
                                                                  @NotNull Class<T> extensionClass, @NotNull PluginDescriptor pluginDescriptor) {
    // todo get rid of it - registerExtensionPoint should be not called several times
    String name = extensionPointName.getName();
    if (extensionArea.hasExtensionPoint(name)) {
      return extensionArea.getExtensionPoint(name);
    }
    else {
      return extensionArea.registerPoint(name, extensionClass, pluginDescriptor, false);
    }
  }


}
