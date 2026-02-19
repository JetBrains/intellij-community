// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.jpackage;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.artifacts.ArtifactBuildTaskProvider;
import org.jetbrains.jps.incremental.BuildTask;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.elements.JpsArchivePackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.plugins.javaFX.JavaFXJpsBundle;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Bas Leijdekkers
 */
public class JPackageArtifactBuildTaskProvider extends ArtifactBuildTaskProvider {
  @Override
  public @NotNull List<JPackageBuildTask> createArtifactBuildTasks(@NotNull JpsArtifact artifact, @NotNull ArtifactBuildPhase buildPhase) {
    if (buildPhase != ArtifactBuildPhase.POST_PROCESSING || !(artifact.getArtifactType() instanceof JPackageJpsArtifactType)) {
      return Collections.emptyList();
    }
    final JpsElement properties = artifact.getProperties();
    if (!(properties instanceof JPackageArtifactProperties)) {
      return Collections.emptyList();
    }
    return Collections.singletonList(new JPackageBuildTask(artifact));
  }

  private static class JPackageBuildTask extends BuildTask {
    private final JpsArtifact myArtifact;

    JPackageBuildTask(JpsArtifact artifact) {
      myArtifact = artifact;
    }

    @Override
    public void build(CompileContext context) throws ProjectBuildException {
      final Set<JpsSdk<?>> sdks = context.getProjectDescriptor().getProjectJavaSdks();
      JpsSdk javaSdk = null;
      for (JpsSdk<?> sdk : sdks) {
        final JpsSdkType<? extends JpsElement> sdkType = sdk.getSdkType();
        if (sdkType instanceof JpsJavaSdkType) {
          javaSdk = sdk;
          break;
        }
      }
      if (javaSdk == null) {
        context.processMessage(new CompilerMessage(JavaFXJpsBundle.message("java.fx.packager"), BuildMessage.Kind.ERROR,
                                                   JavaFXJpsBundle.message("java.version.7.or.higher.is.required.to.build.javafx.package")));
        return;
      }
      new JPackageTool(myArtifact, context).start();
    }
  }

  private static class JPackageTool {
    private static final Logger LOG = Logger.getInstance(JPackageTool.class);

    private final JpsArtifact myArtifact;
    private final JPackageArtifactProperties myProperties;
    private final CompileContext myCompileContext;

    JPackageTool(JpsArtifact artifact, CompileContext context) {
      myArtifact = artifact;
      myProperties = (JPackageArtifactProperties)artifact.getProperties();
      myCompileContext = context;
    }

    public void start() {
      final Set<JpsSdk<?>> sdks = myCompileContext.getProjectDescriptor().getProjectJavaSdks();
      JpsSdk javaSdk = null;
      for (JpsSdk<?> sdk : sdks) {
        final JpsSdkType<? extends JpsElement> sdkType = sdk.getSdkType();
        if (sdkType instanceof JpsJavaSdkType && JpsJavaSdkType.getJavaVersion(sdk) >= 14) {
          javaSdk = sdk;
          break;
        }
      }
      if (javaSdk == null) {
        error(JavaFXJpsBundle.message("java.version.14.or.higher.is.required.to.build.platform.specific.package.using.jpackage"));
        return;
      }
      final String homePath = javaSdk.getHomePath();
      final String jpackagePath = homePath + File.separatorChar + "bin" + File.separatorChar + "jpackage";
      final String archiveName = getArchiveName();
      if (archiveName == null) {
        error(JavaFXJpsBundle.message("no.archive.found"));
        return;
      }

      List<String> commands = new ArrayList<>();
      commands.add(jpackagePath);
      final String outputPath = myArtifact.getOutputFilePath();
      if (myProperties.verbose) {
        commands.add("--verbose");
      }
      addOption(commands, "--input", outputPath);
      addOption(commands, "--main-jar", archiveName);
      addOption(commands, "--name", myArtifact.getName());
      addOption(commands, "--dest", outputPath);

      addOption(commands, "--app-version", myProperties.version);
      addOption(commands, "--copyright", myProperties.copyright);
      addOption(commands, "--description", myProperties.description);
      addOption(commands, "--vendor", myProperties.vendor);
      //addOption(commands, "--icon", myProperties.icon);
      //addOption(commands, "--arguments", myProperties.appArguments);
      //addOption(commands, "--java-options", myProperties.javaArguments);
      addOption(commands, "--main-class", myProperties.mainClass);

      final int errorCode = startProcess(commands);
      if (errorCode != 0) {
        error(JavaFXJpsBundle.message("jpackage.task.has.failed"));
      }
    }

    private static void addOption(List<String> commands, @NotNull String key, @Nullable String value) {
      if (!StringUtil.isEmpty(value)) {
        commands.add(key);
        commands.add(value);
      }
    }

    private String getArchiveName() {
      for (JpsPackagingElement element : myArtifact.getRootElement().getChildren()) {
        if (element instanceof JpsArchivePackagingElement) {
          return ((JpsArchivePackagingElement)element).getArchiveName();
        }
      }
      return null;
    }

    private void error(@Nls String message) {
      myCompileContext.processMessage(new CompilerMessage("jpackage", BuildMessage.Kind.ERROR, message));
    }

    private void info(@Nls String message) {
      myCompileContext.processMessage(new CompilerMessage("jpackage", BuildMessage.Kind.INFO, message));
    }

    private int startProcess(List<String> commands) {
      try {
        final AtomicInteger exitCode = new AtomicInteger();
        final @NlsSafe StringBuilder errorOutput = new StringBuilder();
        final List<@NlsSafe String> delayedInfoOutput = new ArrayList<>();

        final Process process = new ProcessBuilder(CommandLineUtil.toCommandLine(commands)).start();
        BaseOSProcessHandler handler = new BaseOSProcessHandler(process, commands.toString(), null);
        handler.addProcessListener(new ProcessListener() {
          @Override
          public void startNotified(@NotNull ProcessEvent event) {
            if (myProperties.verbose) {
              LOG.info("Started " + commands);
            }
          }

          @Override
          public void processTerminated(@NotNull ProcessEvent event) {
            if (myProperties.verbose) {
              LOG.info("Terminated " + commands + ", exit code: " + event.getExitCode());
            }
            exitCode.set(event.getExitCode());
          }

          @Override
          public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
            String message = StringUtil.trimTrailing(event.getText());
            if (outputType == ProcessOutputTypes.STDERR) {
              LOG.error(message, (Throwable)null);
              errorOutput.append(event.getText());
            }
            else {
              LOG.info(message);
              if (myProperties.verbose) {
                info(message);
              }
              else {
                delayedInfoOutput.add(message);
              }
            }
          }
        });

        handler.startNotify();
        handler.waitFor();

        int result = exitCode.get();
        if (result != 0) {
          final String message = errorOutput.toString();
          if (!StringUtil.isEmptyOrSpaces(message)) {
            error(message);
          }
          for (@NlsSafe String info : delayedInfoOutput) {
            error(info);
          }
        }
        return result;
      }
      catch (Exception e) {
        error(e.getMessage());
        LOG.warn(e);
        return -1;
      }
    }
  }
}
