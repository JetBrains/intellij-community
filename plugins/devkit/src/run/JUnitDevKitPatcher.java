/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.run;

import com.intellij.execution.JUnitPatcher;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.module.Module;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * User: anna
 * Date: Mar 4, 2005
 */
public class JUnitDevKitPatcher extends JUnitPatcher{

  public void patchJavaParameters(@Nullable Module module, JavaParameters javaParameters) {
    final ProjectJdk jdk = javaParameters.getJdk();
    if (jdk == null || !(jdk.getSdkType() instanceof IdeaJdk)) {
      return;
    }
    @NonNls String libPath = jdk.getHomePath() + File.separator + "lib";
    javaParameters.getVMParametersList().add("-Xbootclasspath/p:" + libPath + File.separator + "boot.jar");
    javaParameters.getClassPath().addFirst(libPath + File.separator + "idea.jar");
    javaParameters.getClassPath().addFirst(libPath + File.separator + "resources.jar");
    javaParameters.getClassPath().addFirst(jdk.getToolsPath());
  }
}
