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
package com.intellij.testGuiFramework.framework;

import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

import static com.intellij.openapi.util.io.FileUtil.ensureExists;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Sergey Karashevich
 */
public abstract class IdeTestApplication {

  protected ClassLoader myIdeClassLoader;

  @NotNull
  public ClassLoader getIdeClassLoader() {
    return myIdeClassLoader;
  }

  @NotNull
  public static File getFailedTestScreenshotDirPath() throws IOException {
    File dirPath = new File(getGuiTestRootDirPath(), "failures");
    ensureExists(dirPath);
    return dirPath;
  }

  @NotNull
  protected static File getGuiTestRootDirPath() throws IOException {
    String guiTestRootDirPathProperty = System.getProperty("gui.tests.root.dir.path");
    if (isNotEmpty(guiTestRootDirPathProperty)) {
      File rootDirPath = new File(guiTestRootDirPathProperty);
      if (rootDirPath.isDirectory()) {
        return rootDirPath;
      }
    }
    String homeDirPath = toSystemDependentName(PathManager.getHomePath());
    assertThat(homeDirPath).isNotEmpty();
    File rootDirPath = new File(homeDirPath, "gui-tests");
    ensureExists(rootDirPath);
    return rootDirPath;
  }


}
