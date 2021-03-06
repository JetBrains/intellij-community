// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.execution.cmd;

import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.jetbrains.plugins.gradle.util.GradleDocumentationBundle;

/**
 * @author Vladislav.Soroka
 */
@SuppressWarnings("AccessStaticViaInstance")
public final class GradleCommandLineOptionsProvider {

  private static final Options ourOptions;

  static {
    Options options = new Options();
    // Debugging options, see https://docs.gradle.org/current/userguide/command_line_interface.html#sec:command_line_debugging
    options.addOption(
      OptionBuilder.withLongOpt("full-stacktrace").withDescription(GradleDocumentationBundle.message("gradle.cmd.option.full.stacktrace"))
        .create('S'));
    options.addOption(
      OptionBuilder.withLongOpt("stacktrace").withDescription(GradleDocumentationBundle.message("gradle.cmd.option.stacktrace"))
        .create('s'));
    options.addOption(
      OptionBuilder.withLongOpt("scan")
        .withDescription(GradleDocumentationBundle.message("gradle.cmd.option.scan"))
        .create());

    // Performance options, see https://docs.gradle.org/current/userguide/command_line_interface.html#sec:command_line_performance
    options.addOption(
      OptionBuilder.withLongOpt("build-cache")
        .withDescription(GradleDocumentationBundle.message("gradle.cmd.option.build.cache"))
        .create());
    options.addOption(
      OptionBuilder.withLongOpt("no-build-cache")
        .withDescription(GradleDocumentationBundle.message("gradle.cmd.option.build.cache"))
        .create());
    options.addOption(OptionBuilder.withLongOpt("configure-on-demand").withDescription(
      GradleDocumentationBundle.message("gradle.cmd.option.configure.on.demand")).create());
    options.addOption(OptionBuilder.withLongOpt("no-configure-on-demand").withDescription(
      GradleDocumentationBundle.message("gradle.cmd.option.configure.on.demand")).create());
    options.addOption(OptionBuilder.withLongOpt("max-workers").withDescription(
      GradleDocumentationBundle.message("gradle.cmd.option.max.workers")).create());
    options.addOption(OptionBuilder.withLongOpt("parallel").withDescription(
      GradleDocumentationBundle.message("gradle.cmd.option.parallel")).create());
    options.addOption(OptionBuilder.withLongOpt("no-parallel").withDescription(
      GradleDocumentationBundle.message("gradle.cmd.option.no.parallel")).create());
    options.addOption(OptionBuilder.withLongOpt("priority").withDescription(
      GradleDocumentationBundle.message("gradle.cmd.option.priority"))
                        .create());
    options.addOption(OptionBuilder.withLongOpt("profile")
                        .withDescription(GradleDocumentationBundle.message("gradle.cmd.option.profile"))
                        .create());

    // Logging options, https://docs.gradle.org/current/userguide/command_line_interface.html#sec:command_line_logging
    options.addOption(OptionBuilder.withLongOpt("quiet").withDescription(
      GradleDocumentationBundle.message("gradle.cmd.option.quiet")).create('q'));
    options.addOption(OptionBuilder.withLongOpt("warn").withDescription(
      GradleDocumentationBundle.message("gradle.cmd.option.warn")).create('w'));
    options.addOption(OptionBuilder.withLongOpt("info").withDescription(
      GradleDocumentationBundle.message("gradle.cmd.option.info")).create('i'));
    options.addOption(OptionBuilder.withLongOpt("debug").withDescription(
      GradleDocumentationBundle.message("gradle.cmd.option.debug")).create('d'));
    options.addOption(OptionBuilder.withLongOpt("warning-mode")
                        .hasArg()
                        .withDescription(GradleDocumentationBundle.message("gradle.cmd.option.warning.mode"))
                        .create());

    // Execution options, https://docs.gradle.org/current/userguide/command_line_interface.html#sec:command_line_execution_options
    options.addOption(
      OptionBuilder.withLongOpt("include-build").withDescription(GradleDocumentationBundle.message("gradle.cmd.option.include.build")).create());
    options.addOption(
      OptionBuilder.withLongOpt("offline").withDescription(GradleDocumentationBundle.message("gradle.cmd.option.offline"))
        .create());
    options.addOption(OptionBuilder.withLongOpt("refresh-dependencies").withDescription(
      GradleDocumentationBundle.message("gradle.cmd.option.refresh.dependencies")).create());
    options.addOption(OptionBuilder.withLongOpt("dry-run").withDescription(GradleDocumentationBundle.message("gradle.cmd.option.dry.run")).create());
    options.addOption(OptionBuilder.withLongOpt("write-locks").withDescription(
      GradleDocumentationBundle.message("gradle.cmd.option.write.locks")).create());
    options.addOption(
      OptionBuilder.withLongOpt("update-locks")
        .withDescription(
          GradleDocumentationBundle.message("gradle.cmd.option.update.locks"))
        .hasArg()
        .create());
    options.addOption(OptionBuilder.withLongOpt("no-rebuild").withDescription(
      GradleDocumentationBundle.message("gradle.cmd.option.no.rebuild")).create());

    // Environment options, https://docs.gradle.org/current/userguide/command_line_interface.html#environment_options
    options.addOption(OptionBuilder.withLongOpt("build-file").withDescription(
      GradleDocumentationBundle.message("gradle.cmd.option.build.file")).hasArg().create('b'));
    options.addOption(OptionBuilder.withLongOpt("settings-file").withDescription(
      GradleDocumentationBundle.message("gradle.cmd.option.settings.file")).hasArg().create('c'));
    options.addOption(OptionBuilder.withLongOpt("gradle-user-home").withDescription(
      GradleDocumentationBundle.message("gradle.cmd.option.gradle.user.home")).hasArg().create('g'));
    options.addOption(OptionBuilder
                        .withLongOpt("project-dir")
                        .withDescription(GradleDocumentationBundle.message("gradle.cmd.option.project.dir"))
                        .hasArg().create('p'));
    options.addOption(OptionBuilder.withLongOpt("project-cache-dir").withDescription(
      GradleDocumentationBundle.message("gradle.cmd.option.project.cache.dir")).hasArg().create());
    options.addOption(
      OptionBuilder.withLongOpt("system-prop").withDescription(GradleDocumentationBundle.message("gradle.cmd.option.system.prop"))
        .hasArg().create('D'));
    options
      .addOption(OptionBuilder.withLongOpt("init-script").withDescription(
        GradleDocumentationBundle.message("gradle.cmd.option.init.script")).hasArg().create('I'));
    options.addOption(OptionBuilder.withLongOpt("project-prop")
                        .withDescription(GradleDocumentationBundle.message("gradle.cmd.option.project.prop")).hasArgs()
                        .create('P'));

    // Executing tasks, https://docs.gradle.org/current/userguide/command_line_interface.html#sec:command_line_executing_tasks
    options.addOption(OptionBuilder
                        .withLongOpt("exclude-task")
                        .withDescription(GradleDocumentationBundle.message("gradle.cmd.option.exclude.task"))
                        .hasArgs()
                        .create('x'));
    options.addOption(OptionBuilder
                        .withLongOpt("rerun-tasks")
                        .withDescription(GradleDocumentationBundle.message("gradle.cmd.option.rerun.tasks"))
                        .create());
    options.addOption(OptionBuilder
                        .withLongOpt("continue")
                        .withDescription(GradleDocumentationBundle.message("gradle.cmd.option.continue"))
                        .create());

    // Do not uncomment the following options. These options does not supported via tooling API,
    // https://github.com/gradle/gradle/blob/v6.2.0/subprojects/tooling-api/src/main/java/org/gradle/tooling/LongRunningOperation.java#L149-L154
    //
    // options.addOption(OptionBuilder.withLongOpt("help").withDescription("Shows a help message.").create('h'));
    // options.addOption(OptionBuilder.withLongOpt("version").withDescription("Prints version info.").create('v'));

    ourOptions = options;
  }

  public static Options getSupportedOptions() {
    return ourOptions;
  }
}
