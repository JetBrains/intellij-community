/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.tooling.builder;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.scala.ScalaPlugin;
import org.gradle.api.tasks.scala.ScalaCompile;
import org.gradle.api.tasks.scala.ScalaCompileOptions;
import org.gradle.api.tasks.scala.ScalaForkOptions;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.scala.ScalaModel;
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.internal.scala.ScalaCompileOptionsImpl;
import org.jetbrains.plugins.gradle.tooling.internal.scala.ScalaForkOptionsImpl;
import org.jetbrains.plugins.gradle.tooling.internal.scala.ScalaModelImpl;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 1/31/14
 */
public class ScalaModelBuilderImpl implements ModelBuilderService {

  private static final String COMPILE_SCALA_TASK = "compileScala";

  @Override
  public boolean canBuild(String modelName) {
    return ScalaModel.class.getName().equals(modelName);
  }

  @Override
  public Object buildAll(String modelName, Project project) {
    final ScalaPlugin scalaPlugin = project.getPlugins().findPlugin(ScalaPlugin.class);

    ScalaModel scalaModel = null;
    if (scalaPlugin != null) {
      Task scalaTask = project.getTasks().getByName(COMPILE_SCALA_TASK);
      scalaModel = createModel(scalaTask);
    }
    else {
      Iterator<ScalaCompile> it = project.getTasks().withType(ScalaCompile.class).iterator();
      if (it.hasNext()) {
        scalaModel = createModel(it.next());
      }
    }

    return scalaModel;
  }

  @Nullable
  private static ScalaModel createModel(@Nullable Task task) {
    if (!(task instanceof ScalaCompile)) return null;

    ScalaCompile scalaCompile = (ScalaCompile)task;
    ScalaModelImpl scalaModel = new ScalaModelImpl();
    scalaModel.setScalaClasspath(scalaCompile.getScalaClasspath().getFiles());
    scalaModel.setZincClasspath(scalaCompile.getZincClasspath().getFiles());
    scalaModel.setScalaCompileOptions(create(scalaCompile.getScalaCompileOptions()));
    scalaModel.setTargetCompatibility(scalaCompile.getTargetCompatibility());
    scalaModel.setSourceCompatibility(scalaCompile.getSourceCompatibility());
    return scalaModel;
  }

  @NotNull
  @Override
  public ErrorMessageBuilder getErrorMessageBuilder(@NotNull Project project, @NotNull Exception e) {
    return ErrorMessageBuilder.create(
      project, e, "Scala import errors"
    ).withDescription("Unable to build Scala project configuration");
  }

  @Nullable
  @Contract("null -> null")
  private static ScalaCompileOptionsImpl create(@Nullable ScalaCompileOptions options) {
    if (options == null) return null;

    ScalaCompileOptionsImpl result = new ScalaCompileOptionsImpl();
    result.setAdditionalParameters(wrapStringList(options.getAdditionalParameters()));
    result.setDaemonServer(options.getDaemonServer());
    result.setDebugLevel(options.getDebugLevel());
    result.setDeprecation(options.isDeprecation());
    result.setEncoding(options.getEncoding());
    result.setFailOnError(options.isFailOnError());
    result.setForce(options.getForce());
    result.setFork(options.isFork());
    result.setForkOptions(create(options.getForkOptions()));
    result.setListFiles(options.isListFiles());
    result.setLoggingLevel(options.getLoggingLevel());
    result.setDebugLevel(options.getDebugLevel());
    result.setLoggingPhases(wrapStringList(options.getLoggingPhases()));
    result.setOptimize(options.isOptimize());
    result.setUnchecked(options.isUnchecked());
    result.setUseAnt(options.isUseAnt());
    result.setUseCompileDaemon(options.isUseCompileDaemon());

    return result;
  }

  @Nullable
  private static List<String> wrapStringList(@Nullable List<String> list) {
    if (list == null) return null;
    List<String> strings = new ArrayList<String>();
    for (CharSequence s : list) {
      // fix serialization issue if 's' is an instance of groovy.lang.GString [IDEA-125174]
      strings.add(s.toString());
    }
    return strings;
  }

  @Nullable
  @Contract("null -> null")
  private static ScalaForkOptionsImpl create(@Nullable ScalaForkOptions forkOptions) {
    if (forkOptions == null) return null;

    ScalaForkOptionsImpl result = new ScalaForkOptionsImpl();
    result.setJvmArgs(wrapStringList(forkOptions.getJvmArgs()));
    result.setMemoryInitialSize(forkOptions.getMemoryInitialSize());
    result.setMemoryMaximumSize(forkOptions.getMemoryMaximumSize());
    return result;
  }
}
