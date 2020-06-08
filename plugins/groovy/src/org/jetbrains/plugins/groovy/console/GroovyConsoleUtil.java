// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.console;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.util.ModuleChooserUtil;

import static org.jetbrains.plugins.groovy.bundled.BundledGroovy.getBundledGroovyVersion;
import static org.jetbrains.plugins.groovy.console.GroovyConsoleUtilKt.getApplicableModules;
import static org.jetbrains.plugins.groovy.console.GroovyConsoleUtilKt.sdkVersionIfHasNeededDependenciesToRunConsole;
import static org.jetbrains.plugins.groovy.util.ModuleChooserUtil.formatModuleVersion;

public final class GroovyConsoleUtil {

  @NotNull
  public static String getDisplayGroovyVersion(@NotNull Module module) {
    final String sdkVersion = sdkVersionIfHasNeededDependenciesToRunConsole(module);
    return sdkVersion == null ? "Bundled Groovy " + getBundledGroovyVersion()
                              : "Groovy" + " " + sdkVersion;
  }

  public static void selectModuleAndRun(Project project, Consumer<Module> consumer) {
    ModuleChooserUtil.selectModule(project, getApplicableModules(project), GroovyConsoleUtil::getDisplayGroovyVersion, consumer);
  }

  @NotNull
  public static String getTitle(@NotNull Module module) {
    return formatModuleVersion(module, getDisplayGroovyVersion(module));
  }
}
