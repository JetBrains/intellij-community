// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.execution;

import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.CompositeParameterTargetedValue;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.ide.macro.Macro;
import com.intellij.ide.macro.MacroManager;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.impl.*;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.rt.ant.execution.AntMain2;
import com.intellij.rt.ant.execution.IdeaAntLogger2;
import com.intellij.rt.ant.execution.IdeaInputHandler;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.PathUtil;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AntCommandLineBuilder {
  private final List<@NlsSafe String> myTargets = new ArrayList<>();
  private final SimpleJavaParameters myCommandLine = new SimpleJavaParameters();
  private @NlsSafe String myBuildFilePath;
  private List<BuildFileProperty> myProperties;
  private boolean myDone = false;
  @NonNls private final List<String> myExpandedProperties = new ArrayList<>();
  @NonNls private static final String INPUT_HANDLER_PARAMETER = "-inputhandler";
  @NonNls private static final String LOGFILE_PARAMETER = "-logfile";
  @NonNls private static final String LOGFILE_SHORT_PARAMETER = "-l";
  @NonNls private static final String LOGGER_PARAMETER = "-logger";

  public void calculateProperties(final DataContext dataContext, Project project, List<BuildFileProperty> additionalProperties) throws Macro.ExecutionCancelledException {
    for (BuildFileProperty property : myProperties) {
      expandProperty(dataContext, project, property);
    }
    for (BuildFileProperty property : additionalProperties) {
      expandProperty(dataContext, project, property);
    }
  }

  private void expandProperty(DataContext dataContext, Project project, BuildFileProperty property) throws Macro.ExecutionCancelledException {
    String value = property.getPropertyValue();
    final MacroManager macroManager = GlobalAntConfiguration.getMacroManager();
    value = macroManager.expandMacrosInString(value, true, dataContext);
    value = macroManager.expandMacrosInString(value, false, dataContext);
    value = PathMacroManager.getInstance(project).expandPath(value);
    myExpandedProperties.add("-D" + property.getPropertyName() + "=" + value);
  }

  public void addTarget(@NlsSafe String targetName) {
    myTargets.add(targetName);
  }

  public void setBuildFile(AbstractProperty.AbstractPropertyContainer container, File buildFile) throws CantRunException {
    String jdkName = AntBuildFileImpl.CUSTOM_JDK_NAME.get(container);
    Sdk jdk;
    if (jdkName == null || jdkName.length() <= 0) {
      jdkName = AntConfigurationImpl.DEFAULT_JDK_NAME.get(container);
      if (jdkName == null || jdkName.length() == 0) {
        throw new CantRunException(AntBundle.message("project.jdk.not.specified.error.message"));
      }
    }
    jdk = GlobalAntConfiguration.findJdk(jdkName);
    if (jdk == null) {
      throw new CantRunException(AntBundle.message("jdk.with.name.not.configured.error.message", jdkName));
    }
    VirtualFile homeDirectory = jdk.getHomeDirectory();
    if (homeDirectory == null) {
      throw new CantRunException(AntBundle.message("jdk.with.name.bad.configured.error.message", jdkName));
    }
    myCommandLine.setJdk(jdk);

    final ParametersList vmParametersList = myCommandLine.getVMParametersList();
    vmParametersList.add("-Xmx" + AntBuildFileImpl.MAX_HEAP_SIZE.get(container) + "m");
    vmParametersList.add("-Xss" + AntBuildFileImpl.MAX_STACK_SIZE.get(container) + "m");

    final AntInstallation antInstallation = AntBuildFileImpl.ANT_INSTALLATION.get(container);
    if (antInstallation == null) {
      throw new CantRunException(AntBundle.message("ant.installation.not.configured.error.message"));
    }

    final String antHome = AntInstallation.HOME_DIR.get(antInstallation.getProperties());
    vmParametersList.add(new CompositeParameterTargetedValue("-Dant.home=").addPathPart(antHome));
    final String libraryDir = antHome + (antHome.endsWith("/") || antHome.endsWith(File.separator) ? "" : File.separator) + "lib";
    vmParametersList.add(new CompositeParameterTargetedValue("-Dant.library.dir=").addPathPart(libraryDir));

    String[] urls = jdk.getRootProvider().getUrls(OrderRootType.CLASSES);
    final String jdkHome = homeDirectory.getPath().replace('/', File.separatorChar);
    @NonNls final String pathToJre = jdkHome + File.separator + "jre" + File.separator;
    for (String url : urls) {
      final String path = PathUtil.toPresentableUrl(url);
      if (!path.startsWith(pathToJre)) {
        myCommandLine.getClassPath().add(path);
      }
    }

    myCommandLine.getClassPath().addAllFiles(AntBuildFileImpl.ALL_CLASS_PATH.get(container));

    myCommandLine.getClassPath().addAllFiles(AntBuildFileImpl.getUserHomeLibraries());

    final SdkTypeId sdkType = jdk.getSdkType();
    if (sdkType instanceof JavaSdkType) {
      final String toolsJar = ((JavaSdkType)sdkType).getToolsPath(jdk);
      if (toolsJar != null) {
        myCommandLine.getClassPath().add(toolsJar);
      }
    }
    AntPathUtil.addRtJar(myCommandLine.getClassPath());

    myCommandLine.setMainClass(AntMain2.class.getName());
    final ParametersList programParameters = myCommandLine.getProgramParametersList();

    final String additionalParams = AntBuildFileImpl.ANT_COMMAND_LINE_PARAMETERS.get(container);
    if (additionalParams != null) {
      for (String param : ParametersList.parse(additionalParams)) {
        if (param.startsWith("-J")) {
          final String cutParam = param.substring("-J".length());
          if (cutParam.length() > 0) {
            vmParametersList.add(cutParam);
          }
        }
        else {
          programParameters.add(param);
        }
      }
    }

    if (!(programParameters.getList().contains(LOGGER_PARAMETER))) {
      programParameters.add(LOGGER_PARAMETER, IdeaAntLogger2.class.getName());
    }
    if (!programParameters.getList().contains(INPUT_HANDLER_PARAMETER)) {
      programParameters.add(INPUT_HANDLER_PARAMETER, IdeaInputHandler.class.getName());
    }

    myProperties = AntBuildFileImpl.ANT_PROPERTIES.get(container);

    myBuildFilePath = buildFile.getAbsolutePath();
    myCommandLine.setWorkingDirectory(buildFile.getParent());
  }

  public SimpleJavaParameters getCommandLine() {
    if (myDone) return myCommandLine;
    ParametersList programParameters = myCommandLine.getProgramParametersList();
    for (final String property : myExpandedProperties) {
      if (property != null) {
        programParameters.add(property);
      }
    }
    programParameters.add("-buildfile");
    programParameters.add(new CompositeParameterTargetedValue().addPathPart(myBuildFilePath));
    for (final String target : myTargets) {
      if (target != null) {
        programParameters.add(target);
      }
    }
    myDone = true;
    return myCommandLine;
  }

  public void addTargets(@NlsSafe String[] targets) {
    ContainerUtil.addAll(myTargets, targets);
  }

  public void addTargets(Collection<@NlsSafe String> targets) {
    myTargets.addAll(targets);
  }

  public @NlsSafe String[] getTargets() {
    return ArrayUtilRt.toStringArray(myTargets);
  }
}
