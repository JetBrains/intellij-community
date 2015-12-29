/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.idea;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.application.ConfigImportHelper;
import com.intellij.util.PlatformUtils;

import javax.swing.*;

@SuppressWarnings({"UnusedDeclaration"})
public class MainImpl {
  private MainImpl() { }

  /**
   * Called from PluginManager via reflection.
   */
  protected static void start(final String[] args) {
    System.setProperty(PlatformUtils.PLATFORM_PREFIX_KEY, PlatformUtils.getPlatformPrefix(PlatformUtils.IDEA_CE_PREFIX));

    StartupUtil.prepareAndStart(args, new StartupUtil.AppStarter() {
      @Override
      public void start(final boolean newConfigFolder) {
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            PluginManager.installExceptionHandler();

            if (newConfigFolder && !ConfigImportHelper.isConfigImported()) {
              StartupUtil.runStartupWizard();
            }

            final IdeaApplication app = new IdeaApplication(args);
            //noinspection SSBasedInspection
            SwingUtilities.invokeLater(new Runnable() {
              @Override
              public void run() {
                app.run();
              }
            });
          }
        });
      }
    });
  }
}
