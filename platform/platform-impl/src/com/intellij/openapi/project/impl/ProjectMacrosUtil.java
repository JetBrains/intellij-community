/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.openapi.project.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.WaitForProgressToShow;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class ProjectMacrosUtil {
  private ProjectMacrosUtil() {
  }

  public static boolean showMacrosConfigurationDialog(Project project, final Collection<String> undefinedMacros) {
    final String text = ProjectBundle.message("project.load.undefined.path.variables.message");
    final Application application = ApplicationManager.getApplication();
    if (application.isHeadlessEnvironment() || application.isUnitTestMode()) {
      throw new RuntimeException(text + ": " + StringUtil.join(undefinedMacros, ", "));
    }
    return ShowSettingsUtil.getInstance().editConfigurable(project, new UndefinedMacrosConfigurable(text, undefinedMacros));
  }

  public static boolean checkNonIgnoredMacros(final Project project, final Set<String> usedMacros){
    final PathMacros pathMacros = PathMacros.getInstance();
    for (Iterator<String> iterator = usedMacros.iterator(); iterator.hasNext();) {
      if (pathMacros.isIgnoredMacroName(iterator.next())) {
        iterator.remove();
      }
    }
    return checkMacros(project, usedMacros);
  }

  public static boolean checkMacros(@NotNull final Project project, @NotNull final Set<String> usedMacros) {
    PathMacros pathMacros = PathMacros.getInstance();
    usedMacros.removeAll(pathMacros.getSystemMacroNames());
    usedMacros.removeAll(pathMacros.getUserMacroNames());

    // try to lookup values in System properties
    String pathMacroSystemPrefix = "path.macro.";
    for (Iterator<String> it = usedMacros.iterator(); it.hasNext();) {
      String macro = it.next();
      String value = System.getProperty(pathMacroSystemPrefix + macro, null);
      if (value != null) {
        pathMacros.setMacro(macro, value);
        it.remove();
      }
    }

    if (usedMacros.isEmpty()) {
      // all macros in configuration files are defined
      return true;
    }

    // there are undefined macros, need to define them before loading components
    final boolean[] result = new boolean[1];
    WaitForProgressToShow.runOrInvokeAndWaitAboveProgress(() -> result[0] = showMacrosConfigurationDialog(project, usedMacros), ModalityState.NON_MODAL);
    return result[0];
  }
}