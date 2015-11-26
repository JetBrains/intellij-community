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
package org.jetbrains.plugins.groovy.console;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.AbstractConfigUtils;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.config.GroovyFacetUtil;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;
import org.jetbrains.plugins.groovy.util.ModuleChooserUtil;

import java.util.Arrays;

public class GroovyConsoleUtil {

  public static final Condition<Module> APPLICABLE_MODULE = new Condition<Module>() {
    @Override
    public boolean value(Module module) {
      return GroovyFacetUtil.isSuitableModule(module);
    }
  };

  private static final Function<Module, String> MODULE_VERSION = new Function<Module, String>() {
    @Override
    public String fun(@NotNull Module module) {
      final String moduleGroovyHomePath = LibrariesUtil.getGroovyHomePath(module);
      boolean bundled = moduleGroovyHomePath == null || !hasGroovyAll(module);
      final String homePathToUse = bundled
                                   ? GroovyFacetUtil.getBundledGroovyJar().getParentFile().getParent()
                                   : moduleGroovyHomePath;
      final String version = GroovyConfigUtils.getInstance().getSDKVersion(homePathToUse);
      return version == AbstractConfigUtils.UNDEFINED_VERSION
             ? ""
             : (bundled ? "Bundled " : "") + "Groovy " + version;
    }
  };

  static boolean hasGroovyAll(Module module) {
    final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(module.getProject());
    return (facade.findClass("org.apache.commons.cli.CommandLineParser", scope) != null ||
            facade.findClass("groovyjarjarcommonscli.CommandLineParser", scope) != null) &&
           facade.findClass("groovy.ui.GroovyMain", scope) != null;
  }

  public static void selectModuleAndRun(Project project, Consumer<Module> consumer) {
    ModuleChooserUtil.selectModule(project,
                                   ModuleChooserUtil.filterGroovyCompatibleModules(
                                     Arrays.asList(ModuleManager.getInstance(project).getModules()), APPLICABLE_MODULE),
                                   MODULE_VERSION, consumer);
  }

  public static void selectModuleAndRun(Project project, Consumer<Module> consumer, DataContext context) {
    ModuleChooserUtil.selectModule(project, ModuleChooserUtil.filterGroovyCompatibleModules(
                                     Arrays.asList(ModuleManager.getInstance(project).getModules()), APPLICABLE_MODULE),
                                   MODULE_VERSION, consumer, context);
  }

  @Contract("null -> null; !null -> !null")
  public static String getTitle(@Nullable Module module) {
    return module == null ? null : String.format("%s (%s)", module.getName(), MODULE_VERSION.fun(module));
  }
}
