/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 25-Sep-2007
 */
package com.intellij.openapi.project.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.WaitForProgressToShow;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class ProjectMacrosUtil {
  private static final Logger LOG = Logger.getInstance("#" + ProjectMacrosUtil.class.getName());

  private ProjectMacrosUtil() {
  }

  public static boolean showMacrosConfigurationDialog(Project project, final Collection<String> undefinedMacros) {
    final String text = ProjectBundle.message("project.load.undefined.path.variables.message");
    final Application application = ApplicationManager.getApplication();
    if (application.isHeadlessEnvironment() || application.isUnitTestMode()) {
      throw new RuntimeException(text + ": " + StringUtil.join(undefinedMacros, ", "));
    }
    final UndefinedMacrosConfigurable configurable =
      new UndefinedMacrosConfigurable(text, undefinedMacros);
    final SingleConfigurableEditor editor = new SingleConfigurableEditor(project, configurable);
    editor.show();
    return editor.isOK();
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

  public static boolean checkMacros(final Project project, final Set<String> usedMacros) {
    final Set<String> defined = getDefinedMacros();
    usedMacros.removeAll(defined);

    // try to lookup values in System properties
    @NonNls final String pathMacroSystemPrefix = "path.macro.";
    for (Iterator it = usedMacros.iterator(); it.hasNext();) {
      final String macro = (String)it.next();
      final String value = System.getProperty(pathMacroSystemPrefix + macro, null);
      if (value != null) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            PathMacros.getInstance().setMacro(macro, value);
          }
        });
        it.remove();
      }
    }

    if (usedMacros.isEmpty()) {
      return true; // all macros in configuration files are defined
    }

    // there are undefined macros, need to define them before loading components
    final boolean[] result = new boolean[1];

    final Runnable r = new Runnable() {
      public void run() {
        result[0] = showMacrosConfigurationDialog(project, usedMacros);
      }
    };

    WaitForProgressToShow.runOrInvokeAndWaitAboveProgress(r, ModalityState.NON_MODAL);
    return result[0];
  }

  public static Set<String> getDefinedMacros() {
    final PathMacros pathMacros = PathMacros.getInstance();

    Set<String> definedMacros = new HashSet<String>(pathMacros.getUserMacroNames());
    definedMacros.addAll(pathMacros.getSystemMacroNames());
    definedMacros = Collections.unmodifiableSet(definedMacros);
    return definedMacros;
  }
}