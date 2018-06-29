// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.console;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.config.AbstractConfigUtils;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;
import org.jetbrains.plugins.groovy.util.ModuleChooserUtil;

import static org.jetbrains.plugins.groovy.bundled.BundledGroovy.getBundledGroovyVersion;
import static org.jetbrains.plugins.groovy.console.GroovyConsoleUtilKt.getApplicableModules;
import static org.jetbrains.plugins.groovy.util.ModuleChooserUtil.formatModuleVersion;

public class GroovyConsoleUtil {

  @NotNull
  public static String getDisplayGroovyVersion(@NotNull Module module) {
    final String moduleGroovyHomePath = LibrariesUtil.getGroovyHomePath(module);
    boolean bundled = moduleGroovyHomePath == null || !hasGroovyAll(module);
    final String version;
    if (bundled) {
      version = getBundledGroovyVersion();
    }
    else {
      version = GroovyConfigUtils.getInstance().getSDKVersion(moduleGroovyHomePath);
    }

    final StringBuilder result = new StringBuilder();
    if (bundled) result.append("Bundled ");
    result.append("Groovy");
    if (version != AbstractConfigUtils.UNDEFINED_VERSION) {
      result.append(" ").append(version);
    }
    return result.toString();
  }

  static boolean hasGroovyAll(Module module) {
    final GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module);
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(module.getProject());
    return (facade.findClass("org.apache.commons.cli.CommandLineParser", scope) != null ||
            facade.findClass("groovyjarjarcommonscli.CommandLineParser", scope) != null) &&
           facade.findClass("groovy.ui.GroovyMain", scope) != null;
  }

  public static void selectModuleAndRun(Project project, Consumer<Module> consumer) {
    ModuleChooserUtil.selectModule(project, getApplicableModules(project), GroovyConsoleUtil::getDisplayGroovyVersion, consumer);
  }

  @NotNull
  public static String getTitle(@NotNull Module module) {
    return formatModuleVersion(module, getDisplayGroovyVersion(module));
  }
}
