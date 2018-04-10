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
package com.intellij.execution;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public class CommandLineWrapperUtil {
  @NotNull
  public static File createClasspathJarFile(Manifest manifest, List<String> pathList) throws IOException {
    return createClasspathJarFile(manifest, pathList, false);
  }

  @NotNull
  @SuppressWarnings({"deprecation", "IOResourceOpenedButNotSafelyClosed"})
  public static File createClasspathJarFile(Manifest manifest, List<String> pathList, boolean notEscape) throws IOException {
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

    StringBuilder classPath = new StringBuilder();
    for (String path : pathList) {
      if (classPath.length() > 0) classPath.append(' ');
      File classpathElement = new File(path);
      String url = (notEscape ? classpathElement.toURL() : classpathElement.toURI().toURL()).toString();
      classPath.append(url);
    }
    manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, classPath.toString());

    File jarFile = FileUtil.createTempFile("classpath" + Math.abs(new Random().nextInt()), ".jar", true);
    new JarOutputStream(new BufferedOutputStream(new FileOutputStream(jarFile)), manifest).close();
    return jarFile;
  }
}