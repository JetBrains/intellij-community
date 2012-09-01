/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.mvc;

import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;

import java.util.Set;

/**
 * @author peter
 */
@SuppressWarnings({"UtilityClassWithoutPrivateConstructor", "UtilityClassWithPublicConstructor"})
public class MvcPathMacros {

  public MvcPathMacros() {
    Set<String> macroNames = PathMacros.getInstance().getUserMacroNames();
    for (String framework : ContainerUtil.ar("grails", "griffon")) {
      String name = "USER_HOME_" + framework.toUpperCase();
      if (!macroNames.contains(name)) { // OK, it may appear/disappear during the application lifetime, but we ignore that for now. Restart will help anyway
        PathMacros.getInstance().addLegacyMacro(name, StringUtil.trimEnd(getSdkWorkDirParent(framework), "/"));
      }
    }
  }

  public static String getSdkWorkDirParent(String framework) {
    String grailsWorkDir = System.getProperty(framework + ".work.dir");
    if (StringUtil.isNotEmpty(grailsWorkDir)) {
      grailsWorkDir = FileUtil.toSystemIndependentName(grailsWorkDir);
      if (!grailsWorkDir.endsWith("/")) {
        grailsWorkDir += "/";
      }
      return grailsWorkDir;
    }

    return StringUtil.trimEnd(FileUtil.toSystemIndependentName(SystemProperties.getUserHome()), "/") + "/." + framework + "/";
  }
}
