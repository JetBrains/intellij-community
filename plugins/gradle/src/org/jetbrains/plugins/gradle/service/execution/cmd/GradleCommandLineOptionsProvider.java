// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution.cmd;

import com.intellij.util.containers.ContainerUtil;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleDocumentationBundle;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public final class GradleCommandLineOptionsProvider {

  public static final Options OPTIONS;
  public static final OptionGroup DEBUGGING_OPTIONS;
  public static final OptionGroup PERFORMANCE_OPTIONS;
  public static final OptionGroup LOGGING_OPTIONS;
  public static final OptionGroup EXECUTION_OPTIONS;
  public static final OptionGroup ENVIRONMENT_OPTIONS;
  public static final OptionGroup EXECUTING_TASKS_OPTIONS;
  public static final OptionGroup VERIFICATION_OPTIONS;

  public static final Options UNSUPPORTED_OPTIONS;

  public static final Options TASK_OPTIONS;
  public static final OptionGroup TEST_TASK_OPTIONS;
  public static final OptionGroup BUILT_IN_TASK_OPTIONS;
  public static final OptionGroup COMPOSE_HOT_RELOAD_RUN_TASK_OPTIONS;

  public static Options getSupportedOptions() {
    return OPTIONS;
  }

  public static List<String> getShortOptionsNames(@NotNull Collection<Option> options) {
    return options.stream()
      .map(it -> it.getOpt())
      .filter(it -> it != null)
      .map(it -> "-" + it)
      .collect(Collectors.toList());
  }

  public static List<String> getLongOptionsNames(@NotNull Collection<Option> options) {
    return options.stream()
      .map(it -> it.getLongOpt())
      .filter(it -> it != null)
      .map(it -> "--" + it)
      .collect(Collectors.toList());
  }

  public static List<String> getAllOptionsNames(@NotNull Collection<Option> options) {
    return ContainerUtil.concat(getShortOptionsNames(options), getLongOptionsNames(options));
  }

  static {
    // @formatter:off
    UNSUPPORTED_OPTIONS = new Options()
      // These options aren't supported via tooling API,
      // https://github.com/gradle/gradle/blob/v6.2.0/subprojects/tooling-api/src/main/java/org/gradle/tooling/LongRunningOperation.java#L149-L154
      .addOption(Option.builder("h").longOpt("help").desc(GradleDocumentationBundle.message("gradle.cmd.option.help")).build())
      .addOption(Option.builder("v").longOpt("version").desc(GradleDocumentationBundle.message("gradle.cmd.option.version")).build())
      // The tooling API always runs with the daemon.
      // All the daemon-related options are unsupported by the tooling API.
      // For more details check the according Javadoc of {@link org.gradle.tooling.LongRunningOperation#withArguments}.
      // https://docs.gradle.org/current/userguide/command_line_interface.html#gradle_daemon_options
      .addOption(Option.builder().longOpt("daemon").desc(GradleDocumentationBundle.message("gradle.cmd.option.daemon")).build())
      .addOption(Option.builder().longOpt("no-daemon").desc(GradleDocumentationBundle.message("gradle.cmd.option.no.daemon")).build())
      .addOption(Option.builder().longOpt("status").desc(GradleDocumentationBundle.message("gradle.cmd.option.status")).build())
      .addOption(Option.builder().longOpt("stop").desc(GradleDocumentationBundle.message("gradle.cmd.option.stop")).build())
      .addOption(Option.builder().longOpt("foreground").desc(GradleDocumentationBundle.message("gradle.cmd.option.stop")).build())
    // These options are deprecated
      .addOption(Option.builder("b").longOpt("build-file").desc(GradleDocumentationBundle.message("gradle.cmd.option.build.file")).hasArg().build())
      .addOption(Option.builder("c").longOpt("settings-file").desc(GradleDocumentationBundle.message("gradle.cmd.option.settings.file")).hasArg().build());

    // Debugging options, see https://docs.gradle.org/current/userguide/command_line_interface.html#sec:command_line_debugging
    DEBUGGING_OPTIONS = new OptionGroup()
      .addOption(Option.builder("S").longOpt("full-stacktrace").desc(GradleDocumentationBundle.message("gradle.cmd.option.full.stacktrace")).build())
      .addOption(Option.builder("s").longOpt("stacktrace").desc(GradleDocumentationBundle.message("gradle.cmd.option.stacktrace")).build())
      .addOption(Option.builder().longOpt("scan").desc(GradleDocumentationBundle.message("gradle.cmd.option.scan")).build())
      .addOption(Option.builder().longOpt("no-scan").desc(GradleDocumentationBundle.message("gradle.cmd.option.no.scan")).build());

    // Performance options, see https://docs.gradle.org/current/userguide/command_line_interface.html#sec:command_line_performance
    PERFORMANCE_OPTIONS = new OptionGroup()
      .addOption(Option.builder().longOpt("build-cache").desc(GradleDocumentationBundle.message("gradle.cmd.option.build.cache")).build())
      .addOption(Option.builder().longOpt("no-build-cache").desc(GradleDocumentationBundle.message("gradle.cmd.option.no.build.cache")).build())
      .addOption(Option.builder().longOpt("configuration-cache").desc(GradleDocumentationBundle.message("gradle.cmd.option.configuration.cache")).build())
      .addOption(Option.builder().longOpt("no-configuration-cache").desc(GradleDocumentationBundle.message("gradle.cmd.option.no.configuration.cache")).build())
      .addOption(Option.builder().longOpt("configuration-cache-problems").desc(GradleDocumentationBundle.message("gradle.cmd.option.configuration.cache.problems")).hasArg().build())
      .addOption(Option.builder().longOpt("configure-on-demand").desc(GradleDocumentationBundle.message("gradle.cmd.option.configure.on.demand")).build())
      .addOption(Option.builder().longOpt("no-configure-on-demand").desc(GradleDocumentationBundle.message("gradle.cmd.option.no.configure.on.demand")).build())
      .addOption(Option.builder().longOpt("max-workers").desc(GradleDocumentationBundle.message("gradle.cmd.option.max.workers")).hasArg().build())
      .addOption(Option.builder().longOpt("parallel").desc(GradleDocumentationBundle.message("gradle.cmd.option.parallel")).build())
      .addOption(Option.builder().longOpt("no-parallel").desc(GradleDocumentationBundle.message("gradle.cmd.option.no.parallel")).build())
      .addOption(Option.builder().longOpt("priority").desc(GradleDocumentationBundle.message("gradle.cmd.option.priority")).hasArg().build())
      .addOption(Option.builder().longOpt("profile").desc(GradleDocumentationBundle.message("gradle.cmd.option.profile")).build())
      .addOption(Option.builder().longOpt("watch-fs").desc(GradleDocumentationBundle.message("gradle.cmd.option.watch.fs")).build())
      .addOption(Option.builder().longOpt("no-watch-fs").desc(GradleDocumentationBundle.message("gradle.cmd.option.no.watch.fs")).build());

    // Logging options, https://docs.gradle.org/current/userguide/command_line_interface.html#sec:command_line_logging
    LOGGING_OPTIONS =  new OptionGroup()
      .addOption(Option.builder("q").longOpt("quiet").desc(GradleDocumentationBundle.message("gradle.cmd.option.quiet")).build())
      .addOption(Option.builder("w").longOpt("warn").desc(GradleDocumentationBundle.message("gradle.cmd.option.warn")).build())
      .addOption(Option.builder("i").longOpt("info").desc(GradleDocumentationBundle.message("gradle.cmd.option.info")).build())
      .addOption(Option.builder("d").longOpt("debug").desc(GradleDocumentationBundle.message("gradle.cmd.option.debug")).build())
      .addOption(Option.builder().longOpt("console").desc(GradleDocumentationBundle.message("gradle.cmd.option.console")).hasArg().build())
      .addOption(Option.builder().longOpt("warning-mode").desc(GradleDocumentationBundle.message("gradle.cmd.option.warning.mode")).hasArg().build());

    // Execution options, https://docs.gradle.org/current/userguide/command_line_interface.html#sec:command_line_execution_options
    EXECUTION_OPTIONS = new OptionGroup()
      .addOption(Option.builder().longOpt("include-build").desc(GradleDocumentationBundle.message("gradle.cmd.option.include.build")).hasArg().build())
      .addOption(Option.builder().longOpt("offline").desc(GradleDocumentationBundle.message("gradle.cmd.option.offline")).build())
      .addOption(Option.builder().longOpt("refresh-dependencies").desc(GradleDocumentationBundle.message("gradle.cmd.option.refresh.dependencies")).build())
      .addOption(Option.builder("m").longOpt("dry-run").desc(GradleDocumentationBundle.message("gradle.cmd.option.dry.run")).build())
      .addOption(Option.builder().longOpt("write-locks").desc(GradleDocumentationBundle.message("gradle.cmd.option.write.locks")).build())
      .addOption(Option.builder().longOpt("update-locks").desc(GradleDocumentationBundle.message("gradle.cmd.option.update.locks")).hasArg().build())
      .addOption(Option.builder("a").longOpt("no-rebuild").desc(GradleDocumentationBundle.message("gradle.cmd.option.no.rebuild")).build());

    // Environment options, https://docs.gradle.org/current/userguide/command_line_interface.html#environment_options
    ENVIRONMENT_OPTIONS = new OptionGroup()
      .addOption(Option.builder("g").longOpt("gradle-user-home").desc(GradleDocumentationBundle.message("gradle.cmd.option.gradle.user.home")).hasArg().build())
      .addOption(Option.builder("p").longOpt("project-dir").desc(GradleDocumentationBundle.message("gradle.cmd.option.project.dir")).hasArg().build())
      .addOption(Option.builder().longOpt("project-cache-dir").desc(GradleDocumentationBundle.message("gradle.cmd.option.project.cache.dir")).hasArg().build())
      .addOption(Option.builder("D").longOpt("system-prop").desc(GradleDocumentationBundle.message("gradle.cmd.option.system.prop")).hasArg().build())
      .addOption(Option.builder("I").longOpt("init-script").desc(GradleDocumentationBundle.message("gradle.cmd.option.init.script")).hasArg().build())
      .addOption(Option.builder("P").longOpt("project-prop").desc(GradleDocumentationBundle.message("gradle.cmd.option.project.prop")).hasArg().build());

    // Executing tasks, https://docs.gradle.org/current/userguide/command_line_interface.html#sec:command_line_executing_tasks
    EXECUTING_TASKS_OPTIONS = new OptionGroup()
      .addOption(Option.builder("x").longOpt("exclude-task").desc(GradleDocumentationBundle.message("gradle.cmd.option.exclude.task")).hasArg().build())
      .addOption(Option.builder().longOpt("rerun-tasks").desc(GradleDocumentationBundle.message("gradle.cmd.option.rerun.tasks")).build())
      .addOption(Option.builder().longOpt("continue").desc(GradleDocumentationBundle.message("gradle.cmd.option.continue")).build())
      .addOption(Option.builder("t").longOpt("continuous").desc(GradleDocumentationBundle.message("gradle.cmd.option.continuous")).build());

    // https://docs.gradle.org/current/userguide/dependency_verification.html
    VERIFICATION_OPTIONS = new OptionGroup()
      .addOption(Option.builder().longOpt("export-keys").desc(GradleDocumentationBundle.message("gradle.cmd.option.export.keys")).build())
      .addOption(Option.builder().longOpt("refresh-keys").desc(GradleDocumentationBundle.message("gradle.cmd.option.export.keys")).build())
      .addOption(Option.builder("F").longOpt("dependency-verification").desc(GradleDocumentationBundle.message("gradle.cmd.option.dependency.verification")).hasArg().build())
      .addOption(Option.builder("M").longOpt("write-verification-metadata").desc(GradleDocumentationBundle.message("gradle.cmd.option.write.verification.metadata")).hasArg().build());

    OPTIONS = new Options()
      .addOptionGroup(DEBUGGING_OPTIONS)
      .addOptionGroup(PERFORMANCE_OPTIONS)
      .addOptionGroup(LOGGING_OPTIONS)
      .addOptionGroup(EXECUTION_OPTIONS)
      .addOptionGroup(ENVIRONMENT_OPTIONS)
      .addOptionGroup(EXECUTING_TASKS_OPTIONS)
      .addOptionGroup(VERIFICATION_OPTIONS);

    // https://docs.gradle.org/current/userguide/java_testing.html#sec:test_execution
    TEST_TASK_OPTIONS = new OptionGroup()
      .addOption(Option.builder().longOpt("tests").hasArg().build());

    // https://docs.gradle.org/current/userguide/command_line_interface.html#sec:builtin_task_options
    BUILT_IN_TASK_OPTIONS = new OptionGroup()
      .addOption(Option.builder().longOpt("rerun").desc(GradleDocumentationBundle.message("gradle.cmd.option.rerun")).build());

    // https://github.com/JetBrains/compose-hot-reload/blob/b304b0e1fda3fcf1ca842ca9774fa9712cbf6c61/hot-reload-gradle-plugin/src/main/kotlin/org/jetbrains/compose/reload/gradle/arguments.kt#L103
    // https://github.com/JetBrains/compose-hot-reload/blob/b304b0e1fda3fcf1ca842ca9774fa9712cbf6c61/hot-reload-runtime-jvm/src/main/kotlin/org/jetbrains/compose/reload/jvm/DevApplication.kt#L45
    COMPOSE_HOT_RELOAD_RUN_TASK_OPTIONS = new OptionGroup()
      .addOption(Option.builder().longOpt("mainClass").hasArg().build())
      .addOption(Option.builder().longOpt("className").hasArg().build())
      .addOption(Option.builder().longOpt("funName").hasArg().build());

    TASK_OPTIONS = new Options()
      .addOptionGroup(TEST_TASK_OPTIONS)
      .addOptionGroup(BUILT_IN_TASK_OPTIONS)
      .addOptionGroup(COMPOSE_HOT_RELOAD_RUN_TASK_OPTIONS);
  }
}
