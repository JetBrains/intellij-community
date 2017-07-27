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
import org.jetbrains.backwardRefs.CompilerBackwardReferenceIndex;
import org.jetbrains.backwardRefs.JavacReferenceIndexWriter;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

public class GradleJavacReferenceIndexWriterHolder {

  //TODO replace with common code
  @SuppressWarnings("Duplicates")
  public static void closeIfNeed(boolean clearIndex) {
    CompilerBackwardReferenceIndex ourInstance = BootLoadedHolder.getHeldObject();
    if (ourInstance != null) {
      File dir = clearIndex ? ourInstance.getIndicesDir() : null;
      try {
        ourInstance.close();
      } finally {
        BootLoadedHolder.setHeldObject(null);
        if (dir != null) {
          FileUtilRt.delete(dir);
        }
      }
    }
  }
  public static JavacReferenceIndexWriter getInstance() {
    return BootLoadedHolder.getHeldObject();
  }

  public static void initialize(@NotNull String indexPath) {
      BootLoadedHolder.setHeldObject(new JavacReferenceIndexWriter(new CompilerBackwardReferenceIndex(new File(indexPath), false) {
        @NotNull
        @Override
        protected RuntimeException createBuildDataCorruptedException(IOException cause) {
          // TODO index can be corrupted, so a proper exception should be thrown
          return new RuntimeException(cause);
        }
      }));
  }

  @NotNull
  private static Field getInstanceField() throws ClassNotFoundException, NoSuchFieldException {
    TempUtil.appendToLog("classl " + getRootClassLoader());

    ClassLoader cl = getRootClassLoader();
    while (cl != null) {
      try {
        Class<?> writerClass = Class.forName(
          "org.jetbrains.plugins.gradle.tooling.internal.backRefCollector.GradleJavacReferenceIndexWriterHolder",
          true,
          getRootClassLoader());
        TempUtil.appendToLog("COOL" + cl);
      }
      catch (Exception e) {
        TempUtil.appendToLog("EXCEPTION");
      }
      cl = cl.getParent();
    }

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
    ClassLoader current = Thread.currentThread().getContextClassLoader();
    while (true) {
      if (current.getClass().getName().equals("org.gradle.initialization.MixInLegacyTypesClassLoader")) return current;
      current = current.getParent();
    }
  }
}
