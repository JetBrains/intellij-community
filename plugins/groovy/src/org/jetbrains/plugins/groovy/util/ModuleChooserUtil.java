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
package org.jetbrains.plugins.groovy.util;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesAlphaComparator;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.openapi.util.Condition;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ModuleChooserUtil {

  private static final String GROOVY_LAST_MODULE = "Groovy.Last.Module.Chosen";

  public static void selectModule(@NotNull Project project,
                                  final Collection<Module> suitableModules,
                                  final Function<Module, String> versionProvider,
                                  final Consumer<Module> callback) {
    selectModule(project, suitableModules, versionProvider, callback, null);
  }

  public static void selectModule(@NotNull Project project,
                                  final Collection<Module> suitableModules,
                                  final Function<Module, String> versionProvider,
                                  final Consumer<Module> callback,
                                  @Nullable DataContext context) {
    final List<Module> modules = new ArrayList<>();
    final Map<Module, String> versions = new HashMap<>();

    for (Module module : suitableModules) {
      modules.add(module);
      versions.put(module, versionProvider.fun(module));
    }

    if (modules.size() == 1) {
      callback.consume(modules.get(0));
      return;
    }

    Collections.sort(modules, ModulesAlphaComparator.INSTANCE);

    BaseListPopupStep<Module> step =
      new BaseListPopupStep<Module>("Which module to use classpath of?", modules, PlatformIcons.CONTENT_ROOT_ICON_CLOSED) {
        @NotNull
        @Override
        public String getTextFor(Module value) {
          return String.format("%s (%s)", value.getName(), versions.get(value));
        }

        @Override
        public String getIndexedString(Module value) {
          return value.getName();
        }

        @Override
        public boolean isSpeedSearchEnabled() {
          return true;
        }

        @Override
        public PopupStep onChosen(Module selectedValue, boolean finalChoice) {
          PropertiesComponent.getInstance(selectedValue.getProject()).setValue(GROOVY_LAST_MODULE, selectedValue.getName());
          callback.consume(selectedValue);
          return null;
        }
      };

    final String lastModuleName = PropertiesComponent.getInstance(project).getValue(GROOVY_LAST_MODULE);
    if (lastModuleName != null) {
      int defaultOption = ContainerUtil.indexOf(modules, new Condition<Module>() {
        @Override
        public boolean value(Module module) {
          return module.getName().equals(lastModuleName);
        }
      });
      if (defaultOption >= 0) {
        step.setDefaultOptionIndex(defaultOption);
      }
    }
    final ListPopup listPopup = JBPopupFactory.getInstance().createListPopup(step);
    if (context == null) {
      listPopup.showCenteredInCurrentWindow(project);
    }
    else {
      listPopup.showInBestPositionFor(context);
    }
  }

  @NotNull
  private static Condition<Module> isGroovyCompatibleModule(final Condition<Module> condition) {
    return module -> {
      if (condition.value(module)) {
        final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
        if (sdk != null && sdk.getSdkType() instanceof JavaSdkType) {
          return true;
        }
      }
      return false;
    };
  }

  public static List<Module> filterGroovyCompatibleModules(Collection<Module> modules, final Condition<Module> condition) {
    return ContainerUtil.filter(modules, isGroovyCompatibleModule(condition));
  }

  public static boolean hasGroovyCompatibleModules(Collection<Module> modules, final Condition<Module> condition) {
    return ContainerUtil.or(modules, isGroovyCompatibleModule(condition));
  }
}
