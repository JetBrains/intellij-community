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

import com.intellij.util.Consumer;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Plugin;
import org.jetbrains.backwardRefs.BackwardReferenceIndexUtil;
import org.jetbrains.backwardRefs.JavacReferenceIndexWriter;
import org.jetbrains.backwardRefs.javac.ast.JavacReferenceIndexListener;
import org.jetbrains.backwardRefs.javac.ast.api.JavacFileData;

import javax.tools.*;

public class ReferenceIndexJavacPlugin implements Plugin {
  public static final String PLUGIN_NAME = "ReferenceIndexJavacPlugin";
  public static final String FORK_ARG = "fork";
  public static final String INDEX_PATH_ARG = "index.path=";

  @Override
  public String getName() {
    return PLUGIN_NAME;
  }

  @Override
  public void init(JavacTask task, String... args) {
    boolean inProcessJavac = true;

    TempUtil.appendToLog("co " + ToolProvider.getSystemToolClassLoader());
    TempUtil.appendToLog("co " + task.getClass().getClassLoader());
    TempUtil.appendToLog("co " + getClass().getClassLoader());

    JavacReferenceIndexListener.installOn(task, false, new Consumer<JavacFileData>() {
      @Override
      public void consume(JavacFileData data) {
        try {
          if (inProcessJavac) {
            JavacReferenceIndexWriter writer = GradleJavacReferenceIndexWriterHolder.getInstance();
            if (writer != null) {
              BackwardReferenceIndexUtil.registerFile(data.getFilePath(),
                                                      data.getRefs(),
                                                      data.getDefs(),
                                                      writer);

            }
          }
          else {
            //TODO save em all via message processing
          }
        }
        catch (Exception e) {
          //TempUtil.appendToLog(e);
        }
      }
    });
  }
}
