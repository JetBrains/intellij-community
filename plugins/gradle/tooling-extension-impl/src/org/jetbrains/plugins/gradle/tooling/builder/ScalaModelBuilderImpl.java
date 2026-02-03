// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.builder;

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import com.intellij.gradle.toolingExtension.util.GradleVersionUtil;
import groovy.lang.MetaProperty;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
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
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.internal.scala.ScalaCompileOptionsImpl;
import org.jetbrains.plugins.gradle.tooling.internal.scala.ScalaForkOptionsImpl;
import org.jetbrains.plugins.gradle.tooling.internal.scala.ScalaModelImpl;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

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

  private static @Nullable ScalaModel createModel(@Nullable Task task) {
    if (!(task instanceof ScalaCompile)) return null;

    ScalaCompile scalaCompile = (ScalaCompile)task;
    ScalaModelImpl scalaModel = new ScalaModelImpl();
    scalaModel.setScalaClasspath(new LinkedHashSet<>(scalaCompile.getScalaClasspath().getFiles()));
    scalaModel.setZincClasspath(new LinkedHashSet<>(scalaCompile.getZincClasspath().getFiles()));
    if (GradleVersionUtil.isCurrentGradleAtLeast("6.4")) {
      scalaModel.setScalaCompilerPlugins(new LinkedHashSet<>(scalaCompile.getScalaCompilerPlugins().getFiles()));
    }
    scalaModel.setScalaCompileOptions(create(scalaCompile.getScalaCompileOptions()));
    scalaModel.setTargetCompatibility(scalaCompile.getTargetCompatibility());
    scalaModel.setSourceCompatibility(scalaCompile.getSourceCompatibility());

    return scalaModel;
  }

  @Override
  public void reportErrorMessage(
    @NotNull String modelName,
    @NotNull Project project,
    @NotNull ModelBuilderContext context,
    @NotNull Exception exception
  ) {
    context.getMessageReporter().createMessage()
      .withGroup(Messages.SCALA_PROJECT_MODEL_GROUP)
      .withKind(Message.Kind.ERROR)
      .withTitle("Scala import failure")
      .withText("Unable to build Scala project configuration")
      .withException(exception)
      .reportMessage(project);
  }

  @Contract("null -> null")
  private static @Nullable ScalaCompileOptionsImpl create(@Nullable ScalaCompileOptions options) {
    if (options == null) return null;

    ScalaCompileOptionsImpl result = new ScalaCompileOptionsImpl();
    result.setAdditionalParameters(wrapStringList(options.getAdditionalParameters()));

    MetaProperty daemonServerProperty = DefaultGroovyMethods.hasProperty(options, "daemonServer");
    Object daemonServer = daemonServerProperty != null ? daemonServerProperty.getProperty(options) : null;
    if (daemonServer instanceof String) {
      result.setDaemonServer((String)daemonServer);
    }

    result.setDebugLevel(options.getDebugLevel());
    result.setDeprecation(options.isDeprecation());
    result.setEncoding(options.getEncoding());
    result.setFailOnError(options.isFailOnError());
    result.setForce(String.valueOf(options.isForce()));

    MetaProperty forkProperty = DefaultGroovyMethods.hasProperty(options, "fork");
    Object fork = forkProperty != null ? forkProperty.getProperty(options) : null;
    if (fork instanceof Boolean) {
      result.setFork((Boolean)fork);
    }

    result.setForkOptions(create(options.getForkOptions()));
    result.setListFiles(options.isListFiles());
    result.setLoggingLevel(options.getLoggingLevel());
    result.setDebugLevel(options.getDebugLevel());
    result.setLoggingPhases(wrapStringList(options.getLoggingPhases()));
    result.setOptimize(options.isOptimize());
    result.setUnchecked(options.isUnchecked());

    MetaProperty useAntProperty = DefaultGroovyMethods.hasProperty(options, "useAnt");
    Object useAnt = useAntProperty != null ? useAntProperty.getProperty(options) : null;
    if (useAnt instanceof Boolean) {
      result.setUseAnt((Boolean)useAnt);
    }

    MetaProperty useCompileDaemonProperty = DefaultGroovyMethods.hasProperty(options, "useCompileDaemon");
    Object useCompileDaemon = useCompileDaemonProperty != null ? useCompileDaemonProperty.getProperty(options) : null;
    if (useCompileDaemon instanceof Boolean) {
      result.setUseCompileDaemon((Boolean)useCompileDaemon);
    }

    return result;
  }

  private static @Nullable List<String> wrapStringList(@Nullable List<?> list) {
    if (list == null) return null;
    // fix serialization issue if 's' is an instance of groovy.lang.GString [IDEA-125174]
    //noinspection SSBasedInspection
    return list.stream().map(x -> x.toString()).collect(Collectors.toList());
  }

  @Contract("null -> null")
  private static @Nullable ScalaForkOptionsImpl create(@Nullable ScalaForkOptions forkOptions) {
    if (forkOptions == null) return null;

    ScalaForkOptionsImpl result = new ScalaForkOptionsImpl();
    result.setJvmArgs(wrapStringList(forkOptions.getJvmArgs()));
    result.setMemoryInitialSize(forkOptions.getMemoryInitialSize());
    result.setMemoryMaximumSize(forkOptions.getMemoryMaximumSize());

    return result;
  }
}
