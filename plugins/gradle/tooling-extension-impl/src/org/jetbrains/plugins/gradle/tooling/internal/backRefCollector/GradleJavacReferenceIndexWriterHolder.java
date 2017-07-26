/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.tooling.internal.backRefCollector;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.backwardRefs.CompilerBackwardReferenceIndex;
import org.jetbrains.jps.backwardRefs.JavacReferenceIndexWriter;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

public class GradleJavacReferenceIndexWriterHolder {
  private static volatile JavacReferenceIndexWriter ourInstance;

  //TODO replace with common code
  @SuppressWarnings("Duplicates")
  public static void closeIfNeed(boolean clearIndex) {
    if (ourInstance != null) {
      File dir = clearIndex ? ourInstance.getIndicesDir() : null;
      try {
        ourInstance.close();
      } finally {
        ourInstance = null;
        if (dir != null) {
          FileUtilRt.delete(dir);
        }
      }
    }
  }
  public static JavacReferenceIndexWriter getInstance() {
    try {
      Field instanceField = getInstanceField();
      return (JavacReferenceIndexWriter)instanceField.get(null);
    }
    catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }

  public static void initialize(/*CompileContext*/) {
    //TODO project "context"
    try {
      getInstanceField().set(null, new JavacReferenceIndexWriter(new CompilerBackwardReferenceIndex(new File("/home/user/index"), false) {
        @NotNull
        @Override
        protected RuntimeException createBuildDataCorruptedException(IOException cause) {
          // TODO index can be corrupted, so a proper exception should be thrown
          return new RuntimeException(cause);
        }
      }));
    }
    catch (IllegalAccessException | ClassNotFoundException | NoSuchFieldException e) {
      throw new IllegalStateException(e);
    }
  }

  @NotNull
  private static Field getInstanceField() throws ClassNotFoundException, NoSuchFieldException {
    Class<?> writerClass = Class.forName(
      "org.jetbrains.plugins.gradle.tooling.internal.backRefCollector.GradleJavacReferenceIndexWriterHolder",
      true,
      getRootClassLoader());
    Field instanceField = writerClass.getDeclaredField("ourInstance");
    instanceField.setAccessible(true);
    return instanceField;
  }

  @NotNull
  private static ClassLoader getRootClassLoader() {
    ClassLoader current = GradleJavacReferenceIndexWriterHolder.class.getClassLoader();
    while (true) {
      ClassLoader parent = current.getParent();
      if (parent == null || "sun.misc.Launcher$ExtClassLoader".equals(parent.getClass().getName())) return current;
      current = parent;
    }
  }
}
