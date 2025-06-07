// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.ant.build;

import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.lang.ant.config.impl.BuildFileProperty;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.rt.ant.execution.AntMain2;
import com.intellij.util.PathUtilRt;
import com.intellij.util.SystemProperties;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ant.AntJpsBundle;
import org.jetbrains.jps.ant.model.JpsAntBuildFileOptions;
import org.jetbrains.jps.ant.model.JpsAntExtensionService;
import org.jetbrains.jps.ant.model.JpsAntInstallation;
import org.jetbrains.jps.ant.model.artifacts.JpsAntArtifactExtension;
import org.jetbrains.jps.ant.model.impl.JpsAntInstallationImpl;
import org.jetbrains.jps.builders.artifacts.ArtifactBuildTaskProvider;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.messages.CompilerMessage;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.service.JpsServiceManager;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AntArtifactBuildTaskProvider extends ArtifactBuildTaskProvider {
  private static final Logger LOG = Logger.getInstance(AntArtifactBuildTaskProvider.class);

  @Override
  public @NotNull List<? extends BuildTask> createArtifactBuildTasks(@NotNull JpsArtifact artifact, @NotNull ArtifactBuildPhase buildPhase) {
    JpsAntArtifactExtension extension = getBuildExtension(artifact, buildPhase);
    if (extension != null && extension.isEnabled() && !StringUtil.isEmpty(extension.getFileUrl())) {
      return Collections.singletonList(new AntArtifactBuildTask(extension));
    }
    return Collections.emptyList();
  }

  private static @Nullable JpsAntArtifactExtension getBuildExtension(JpsArtifact artifact, ArtifactBuildPhase buildPhase) {
    switch (buildPhase) {
      case PRE_PROCESSING:
        return JpsAntExtensionService.getPreprocessingExtension(artifact);
      case POST_PROCESSING:
        return JpsAntExtensionService.getPostprocessingExtension(artifact);
      default:
        return null;
    }
  }

  private static final class AntArtifactBuildTask extends BuildTask {
    public static final @NlsSafe String BUILDER_NAME = "ant";
    private final JpsAntArtifactExtension myExtension;

    AntArtifactBuildTask(@NotNull JpsAntArtifactExtension extension) {
      myExtension = extension;
    }

    @Override
    public void build(final CompileContext context) throws ProjectBuildException {
      JpsProject project = context.getProjectDescriptor().getProject();
      JpsAntBuildFileOptions options = JpsAntExtensionService.getOptions(project, myExtension.getFileUrl());

      JpsTypedLibrary<JpsSdk<JpsDummyElement>> jdkLibrary;
      String jdkName = options.getCustomJdkName();
      if (!StringUtil.isEmpty(jdkName)) {
        jdkLibrary = project.getModel().getGlobal().getLibraryCollection().findLibrary(jdkName, JpsJavaSdkType.INSTANCE);
        if (jdkLibrary == null) {
          reportError(context, "JDK '" + jdkName + "' not found");
          throw new StopBuildException();
        }
      }
      else {
        JpsSdkReference<JpsDummyElement> reference = project.getSdkReferencesTable().getSdkReference(JpsJavaSdkType.INSTANCE);
        if (reference == null) {
          reportError(context, "project JDK is not specified");
          throw new StopBuildException();
        }

        jdkLibrary = reference.resolve();
        if (jdkLibrary == null) {
          reportError(context, "JDK '" + reference.getSdkName() + "' not found");
          throw new StopBuildException();
        }
      }
      JpsSdk<?> jdk = jdkLibrary.getProperties();

      JpsAntInstallation antInstallation = JpsAntExtensionService.getAntInstallationForBuildFile(context.getProjectDescriptor().getModel(),
                                                                                                 myExtension.getFileUrl());
      if (antInstallation == null) {
        reportError(context, "Ant installation is not configured");
        throw new StopBuildException();
      }

      List<String> classpath = new ArrayList<>();
      File jreHome = new File(jdk.getHomePath(), "jre");
      for (File file : jdkLibrary.getFiles(JpsOrderRootType.COMPILED)) {
        if (!FileUtil.isAncestor(jreHome, file, false)) {
          classpath.add(file.getAbsolutePath());
        }
      }
      classpath.addAll(options.getAdditionalClasspath());
      classpath.addAll(antInstallation.getClasspath());
      JpsAntInstallationImpl.addAllJarsFromDirectory(classpath, new File(SystemProperties.getUserHome(), ".ant/lib"));
      classpath.add(PathManager.getJarPathForClass(AntMain2.class));

      List<String> vmParams = new ArrayList<>();
      vmParams.add("-Xmx" + options.getMaxHeapSize() + "m");
      vmParams.add("-Xss" + options.getMaxStackSize() + "m");
      vmParams.add("-Dant.home=" + antInstallation.getAntHome().getAbsolutePath());

      List<String> programParams = new ArrayList<>();
      for (String param : ParametersListUtil.parse(options.getAntCommandLineParameters())) {
        if (param.startsWith("-J")) {
          String vmParam = StringUtil.trimStart(param, "-J");
          if (!vmParam.isEmpty()) {
            vmParams.add(vmParam);
          }
        }
        else {
          programParams.add(param);
        }
      }

      for (List<BuildFileProperty> properties : Arrays.asList(myExtension.getAntProperties(), options.getProperties())) {
        for (BuildFileProperty property : properties) {
          programParams.add("-D" + property.getPropertyName() + "=" + property.getPropertyValue());
        }
      }
      programParams.add("-buildfile");
      final String buildFilePath = JpsPathUtil.urlToPath(myExtension.getFileUrl());
      programParams.add(buildFilePath);
      final String targetName = myExtension.getTargetName();
      if (targetName != null) {
        programParams.add(targetName);
      }

      @NlsSafe String buildFileName = PathUtilRt.getFileName(buildFilePath);

      String targetDisplayName = targetName != null ? targetName : AntJpsBundle.message("ant.target.default.name");
      context.processMessage(new ProgressMessage(AntJpsBundle.message("running.ant.target.from.file", targetDisplayName, buildFileName)));
      Iterable<AntBuildTaskListener> listeners = JpsServiceManager.getInstance().getExtensions(AntBuildTaskListener.class);
      for (AntBuildTaskListener listener : listeners) {
        listener.beforeAntBuildTaskStarted(myExtension, vmParams, programParams);
      }

      List<String> commandLine = ExternalProcessUtil.buildJavaCommandLine(JpsJavaSdkType.getJavaExecutable(jdk), AntMain2.class.getName(),
                                                                          Collections.emptyList(), classpath, vmParams, programParams,
                                                                          false);
      try {
        Process process = new ProcessBuilder(commandLine).directory(new File(buildFilePath).getParentFile()).start();
        String commandLineString = StringUtil.join(commandLine, " ");
        if (LOG.isDebugEnabled()) {
          LOG.debug("Starting ant target:" + commandLineString);
        }
        BaseOSProcessHandler handler = new BaseOSProcessHandler(process, commandLineString, null);
        final AtomicBoolean hasErrors = new AtomicBoolean();
        final @NlsSafe StringBuilder errorOutput = new StringBuilder();
        handler.addProcessListener(new ProcessListener() {
          @Override
          public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
            if (outputType == ProcessOutputTypes.STDERR) {
              errorOutput.append(event.getText());
            }
          }

          @Override
          public void processTerminated(@NotNull ProcessEvent event) {
            int exitCode = event.getExitCode();
            if (exitCode != 0) {
              context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, errorOutput.toString()));

              String message = AntJpsBundle.message("target.finished.with.exit.code", targetName, buildFilePath, exitCode);
              context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR, message));
              hasErrors.set(true);
            }
          }
        });
        handler.startNotify();
        handler.waitFor();
        if (hasErrors.get()) {
          throw new StopBuildException();
        }
      }
      catch (IOException e) {
        throw new ProjectBuildException(e);
      }
    }

    private void reportError(CompileContext context, final @NlsSafe String text) {
      context.processMessage(new CompilerMessage(BUILDER_NAME, BuildMessage.Kind.ERROR,
                                                 AntJpsBundle.message("cannot.run.target", myExtension.getTargetName(), text)));
    }
  }
}
