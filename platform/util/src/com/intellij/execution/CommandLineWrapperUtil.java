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
package com.intellij.execution;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipOutputStream;

public class CommandLineWrapperUtil {
  @NotNull
  public static File createClasspathJarFile(Manifest manifest, List<String> pathList) throws IOException {
    return createClasspathJarFile(manifest, pathList, false);
  }

  @NotNull
  public static File createClasspathJarFile(Manifest manifest, List<String> pathList, final boolean notEscape) throws IOException {
    final Attributes attributes = manifest.getMainAttributes();
    attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");

    final Ref<IOException> ex = new Ref<IOException>();
    final String classPathAttribute = StringUtil.join(pathList, new Function<String, String>() {
      @Override
      public String fun(String path) {
        final File classpathElement = new File(path);
        try {
          return (notEscape ? classpathElement.toURL() : classpathElement.toURI().toURL()).toString();
        }
        catch (IOException e) {
          ex.set(e);
          return null;
        }
      }
    }, " ");
    
    final IOException thrownException = ex.get();
    if (thrownException != null) {
      throw thrownException;
    }
    attributes.put(Attributes.Name.CLASS_PATH, classPathAttribute);

    File jarFile = FileUtil.createTempFile("classpath", ".jar", true);
    ZipOutputStream jarPlugin = null;
    try {
      BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(jarFile));
      jarPlugin = new JarOutputStream(out, manifest);
    }
    finally {
      if (jarPlugin != null) jarPlugin.close();
    }
    return jarFile;
  }
}
