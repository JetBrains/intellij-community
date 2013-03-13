/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.packaging;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.util.io.ZipUtil;

import java.io.File;
import java.io.IOException;

/**
 * User: anna
 * Date: 3/12/13
 */
public class JavaFxPackagerUtil {
  private static final Logger LOG = Logger.getInstance("#" + JavaFxArtifactProperties.class.getName());

  public static void createJarAndDeploy(final Artifact artifact,
                                        final CompileContext compileContext,
                                        final String binPath,
                                        final JavaFxArtifactProperties properties) {
    final String zipPath = artifact.getOutputFilePath();

    final File tempUnzippedArtifactOutput;
    try {
      tempUnzippedArtifactOutput = FileUtil.createTempDirectory("artifact", "unzipped");
      ZipUtil.extract(new File(zipPath), tempUnzippedArtifactOutput, null);
    }
    catch (IOException e) {
      registerJavaFxPackagerError(compileContext, e);
      return;
    }

    final GeneralCommandLine commandLine = new GeneralCommandLine();
    try {
      commandLine.setExePath(binPath + File.separator + "javafxpackager");

      commandLine.addParameter("-createJar");
      commandLine.addParameter("-appclass");
      commandLine.addParameter(properties.getAppClass());

      commandLine.addParameter("-srcdir");
      commandLine.addParameter(tempUnzippedArtifactOutput.getPath());
      commandLine.addParameter("-outdir");

      final File tempDirWithJar;
      try {
        tempDirWithJar = FileUtil.createTempDirectory("javafxpackager", "out");
      }
      catch (IOException e) {
        registerJavaFxPackagerError(compileContext, e);
        return;
      }
      commandLine.addParameter(tempDirWithJar.getPath());
      commandLine.addParameter("-outfile");

      commandLine.addParameter(artifact.getName());
      commandLine.addParameter("-v");

      commandLine.addParameter("-nocss2bin");
      
      appendManifestProperties(commandLine, properties);

      final MyOnTerminatedProcessAdapter adapter = new MyOnTerminatedProcessAdapter(compileContext) {
        @Override
        protected void onTerminated() {
          deploy(artifact, compileContext, binPath, properties, tempDirWithJar, tempUnzippedArtifactOutput);
        }
      };
      startProcess(commandLine, adapter);
    }
    catch (ExecutionException ex) {
      registerJavaFxPackagerError(compileContext, ex);
    }
  }

  private static void registerJavaFxPackagerError(CompileContext compileContext, Exception ex) {
    registerJavaFxPackagerError(compileContext, ex.getMessage());
  }

  private static void registerJavaFxPackagerError(CompileContext compileContext, final String message) {
    compileContext.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
  }

  private static void deploy(final Artifact artifact,
                             final CompileContext compileContext,
                             String binPath,
                             JavaFxArtifactProperties properties,
                             final File tempDirWithCreatedJar, 
                             final File tempUnzippedArtifactOutput) {
    final GeneralCommandLine commandLine = new GeneralCommandLine();
    try {
      commandLine.setExePath(binPath + File.separator + "javafxpackager");

      commandLine.addParameter("-deploy");

      final String title = properties.getTitle();
      if (!StringUtil.isEmptyOrSpaces(title)) {
        commandLine.addParameter("-title");
        commandLine.addParameter(title);
      }
      final String vendor = properties.getVendor();
      if (!StringUtil.isEmptyOrSpaces(vendor)) {
        commandLine.addParameter("-vendor");
        commandLine.addParameter(vendor);
      }
      final String description = properties.getDescription();
      if (!StringUtil.isEmptyOrSpaces(description)) {
        commandLine.addParameter("-description");
        commandLine.addParameter(description);
      }

      commandLine.addParameter("-appclass");
      commandLine.addParameter(properties.getAppClass());

      commandLine.addParameter("-width");
      commandLine.addParameter("600");
      commandLine.addParameter("-height");
      commandLine.addParameter("400");

      commandLine.addParameter("-name");
      commandLine.addParameter(artifact.getName());

      
      commandLine.addParameter("-outdir");

      final File tempDirectory;
      try {
        tempDirectory = FileUtil.createTempDirectory("javafxpackager", "out");
      }
      catch (IOException e) {
        registerJavaFxPackagerError(compileContext, e);
        return;
      }
      commandLine.addParameter(tempDirectory.getPath());

      commandLine.addParameter("-outfile");
      commandLine.addParameter(artifact.getName());

      commandLine.addParameter("-srcdir");
      commandLine.addParameter(tempDirWithCreatedJar.getPath());

      commandLine.addParameter("-v");

      final MyOnTerminatedProcessAdapter adapter = new MyOnTerminatedProcessAdapter(compileContext) {
        @Override
        protected void onTerminated() {
          FileUtil.delete(tempUnzippedArtifactOutput);
          FileUtil.delete(new File(artifact.getOutputFilePath()));
          copyResultsToArtifactsOutput(tempDirectory);
          copyResultsToArtifactsOutput(tempDirWithCreatedJar);
        }

        private void copyResultsToArtifactsOutput(final File tempDirectory) {
          try {
            final File resultedJar = new File(artifact.getOutputPath());
            FileUtil.copyDir(tempDirectory, resultedJar);
          }
          catch (IOException e) {
            LOG.info(e);
          }
          FileUtil.delete(tempDirectory);
        }
      };
      startProcess(commandLine, adapter);
    }
    catch (ExecutionException ex) {
      registerJavaFxPackagerError(compileContext, ex);
    }
  }

  private static void appendManifestProperties(GeneralCommandLine commandLine, final JavaFxArtifactProperties properties) {
    final String manifestString = properties.getManifestString();
    if (manifestString != null) {
      commandLine.addParameter("-manifestAttrs");
      commandLine.addParameter("\"" + manifestString + "\"");
    }
  }

  private static void startProcess(GeneralCommandLine commandLine, MyOnTerminatedProcessAdapter adapter)
    throws ExecutionException {
    final OSProcessHandler handler = new OSProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString());
    adapter.setHandler(handler);
    handler.addProcessListener(adapter);
    handler.startNotify();
  }

  private static abstract class MyOnTerminatedProcessAdapter extends ProcessAdapter {
    private OSProcessHandler myHandler;
    private final CompileContext myCompileContext;
    
    public MyOnTerminatedProcessAdapter(CompileContext compileContext) {
      myCompileContext = compileContext;
    }

    private void setHandler(OSProcessHandler handler) {
      myHandler = handler;
    }

    @Override
    public void processTerminated(ProcessEvent event) {
      myHandler.removeProcessListener(this);
      onTerminated();
    }

    protected abstract void onTerminated();

    @Override
    public void onTextAvailable(ProcessEvent event, Key outputType) {
      if (outputType == ProcessOutputTypes.STDERR) {
        registerJavaFxPackagerError(myCompileContext, event.getText());
      }
    }
  }
}
