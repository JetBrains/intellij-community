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

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.ArtifactPropertiesEditor;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * User: anna
 * Date: 3/12/13
 */
public class JavaFxArtifactProperties extends ArtifactProperties<JavaFxArtifactProperties> {

  private String myTitle;
  private String myVendor;
  private String myDescription;
  private String myAppClass;

  @Override
  public void onBuildFinished(@NotNull final Artifact artifact, @NotNull final CompileContext compileContext) {
    if (!(artifact.getArtifactType() instanceof JavaFxApplicationArtifactType)) {
      return;
    }
    final Project project = compileContext.getProject();
    final Set<Module> modules = ApplicationManager.getApplication().runReadAction(new Computable<Set<Module>>() {
      @Override
      public Set<Module> compute() {
        return ArtifactUtil.getModulesIncludedInArtifacts(Collections.singletonList(artifact), project);
      }
    });
    if (modules.isEmpty()) {
      return;
    }

    Sdk fxCompatibleSdk = null;
    for (Module module : modules) {
      final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
      if (sdk != null && sdk.getSdkType() instanceof JavaSdk) {
        if (((JavaSdk)sdk.getSdkType()).isOfVersionOrHigher(sdk, JavaSdkVersion.JDK_1_7)) {
          fxCompatibleSdk = sdk;
          break;
        }
      }
    }

    if (fxCompatibleSdk == null) {
      compileContext.addMessage(CompilerMessageCategory.ERROR, "Java version 7 or higher is required to build JavaFX package", null, -1, -1);
      return;
    }

    final String binPath = ((JavaSdk)fxCompatibleSdk.getSdkType()).getBinPath(fxCompatibleSdk);

    final JavaFxArtifactProperties properties =
            (JavaFxArtifactProperties)artifact.getProperties(JavaFxArtifactPropertiesProvider.getInstance());
    
    if (StringUtil.isEmptyOrSpaces(properties.getAppClass())) {
      compileContext.addMessage(CompilerMessageCategory.ERROR, "No application class specified for JavaFX package", null, -1, -1);
      return;
    }

    new JavaFxPackager(artifact, properties, compileContext).createJarAndDeploy(binPath);
  }

  @Override
  public ArtifactPropertiesEditor createEditor(@NotNull ArtifactEditorContext context) {
    return new JavaFxArtifactPropertiesEditor(this, context.getProject(), context.getArtifact());
  }

  @Nullable
  @Override
  public JavaFxArtifactProperties getState() {
    return this;
  }

  @Override
  public void loadState(JavaFxArtifactProperties state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public String getTitle() {
    return myTitle;
  }

  public void setTitle(String title) {
    myTitle = title;
  }

  public String getVendor() {
    return myVendor;
  }

  public void setVendor(String vendor) {
    myVendor = vendor;
  }

  public String getDescription() {
    return myDescription;
  }

  public void setDescription(String description) {
    myDescription = description;
  }

  public String getAppClass() {
    return myAppClass;
  }

  public void setAppClass(String appClass) {
    myAppClass = appClass;
  }

  private static class JavaFxPackager extends AbstractJavaFxPackager {
    private final Artifact myArtifact;
    private final JavaFxArtifactProperties myProperties;
    private final CompileContext myCompileContext;

    public JavaFxPackager(Artifact artifact, JavaFxArtifactProperties properties, CompileContext compileContext) {
      myArtifact = artifact;
      myProperties = properties;
      myCompileContext = compileContext;
    }

    @Override
    protected String getArtifactName() {
      return myArtifact.getName();
    }

    @Override
    protected String getArtifactOutputPath() {
      return myArtifact.getOutputPath();
    }

    @Override
    protected String getArtifactOutputFilePath() {
      return myArtifact.getOutputFilePath();
    }

    @Override
    protected String getAppClass() {
      return myProperties.getAppClass();
    }

    @Override
    protected String getTitle() {
      return myProperties.getTitle();
    }

    @Override
    protected String getVendor() {
      return myProperties.getVendor();
    }

    @Override
    protected String getDescription() {
      return myProperties.getDescription();
    }

    @Override
    protected void registerJavaFxPackagerError(String message) {
      myCompileContext.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
    }

    @Override
    protected void addParameter(List<String> commandLine, String param) {
      super.addParameter(commandLine, GeneralCommandLine.prepareCommand(param));
    }
  }
}
