// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.application.options.RegistryManager;
import com.intellij.ide.plugins.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import org.jetbrains.annotations.NotNull;

/**
 * Forces JCEF early startup in order to support co-existence with JavaFX (see IDEA-236310).
 */
public final class JBCefStartup {
  @SuppressWarnings({"unused", "FieldCanBeLocal"})
  private JBCefClient STARTUP_CLIENT; // auto-disposed along with JBCefApp on IDE shutdown

  // os=mac
  JBCefStartup() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }
    if (JBCefApp.isSupported() && RegistryManager.getInstance().is("ide.browser.jcef.preinit")) {
      try {
        STARTUP_CLIENT = JBCefApp.getInstance().createClient();
      }
      catch (IllegalStateException ignore) {
      }
    } else {
      //todo[tav] remove when JavaFX + JCEF co-exist is fixed on macOS, or when JavaFX is deprecated
      //This code enables pre initialization of JCEF on macOS if and only if JavaFX Runtime plugin is installed
      PluginManager pluginManager = PluginManager.getInstance();
      String id = "com.intellij.javafx";
      PluginId javaFX = PluginId.findId(id);

      if ( javaFX == null || pluginManager.findEnabledPlugin(javaFX) == null) {
        ApplicationManager.getApplication().getMessageBus().connect()
          .subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
            @Override
            public void pluginLoaded(@NotNull IdeaPluginDescriptor pluginDescriptor) {
              if (pluginDescriptor.getPluginId().getIdString().equals(id)) {
                RegistryManager.getInstance().get("ide.browser.jcef.preinit").setValue(true);
              }
            }
          });
      }
    }
  }
}
