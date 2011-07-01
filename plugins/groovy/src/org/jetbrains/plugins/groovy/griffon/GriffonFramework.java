/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.griffon;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.IgnoredBeanFactory;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.mvc.MvcFramework;
import org.jetbrains.plugins.groovy.mvc.MvcModuleStructureUtil;
import org.jetbrains.plugins.groovy.mvc.MvcPathMacros;
import org.jetbrains.plugins.groovy.mvc.MvcProjectStructure;

import javax.swing.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * @author peter
 */
public class GriffonFramework extends MvcFramework {
  public static final Icon GRIFFON_ICON = IconLoader.getIcon("/icons/griffon/griffon.png");
  @NonNls private static final String GRIFFON_COMMON_PLUGINS = "-griffonPlugins";
  private static final String GLOBAL_PLUGINS_MODULE_NAME = "GriffonGlobalPlugins";

  public static final String GRIFFON_USER_LIBRARY = "Griffon:lib";

  private GriffonFramework() {
  }

  public boolean hasSupport(@NotNull Module module) {
    return getSdkRoot(module) != null && findAppRoot(module) != null && !isAuxModule(module);
  }

  @Override
  public String getApplicationDirectoryName() {
    return "griffon-app";
  }

  @Override
  public boolean isToReformatOnCreation(VirtualFile file) {
    return file.getFileType() == GroovyFileType.GROOVY_FILE_TYPE;
  }

  @Override
  public void upgradeFramework(@NotNull Module module) {
  }

  @Override
  public boolean updatesWholeProject() {
    return false;
  }

  @Override
  public void updateProjectStructure(final @NotNull Module module) {
    if (!MvcModuleStructureUtil.isEnabledStructureUpdate()) return;

    final VirtualFile root = findAppRoot(module);
    if (root == null) return;

    AccessToken token = WriteAction.start();
    try {
      MvcModuleStructureUtil.updateModuleStructure(module, createProjectStructure(module, false), root);

      if (hasSupport(module)) {
        MvcModuleStructureUtil.updateAuxiliaryPluginsModuleRoots(module, getInstance());
        MvcModuleStructureUtil.updateGlobalPluginModule(module.getProject(), getInstance());
      }
    }
    finally {
      token.finish();
    }

    final Project project = module.getProject();
    ChangeListManager.getInstance(project).addFilesToIgnore(IgnoredBeanFactory.ignoreUnderDirectory(getUserHomeGriffon(), project));
  }

  @Override
  public void ensureRunConfigurationExists(@NotNull Module module) {
    final VirtualFile root = findAppRoot(module);
    if (root != null) {
      ensureRunConfigurationExists(module, GriffonRunConfigurationType.getInstance(), "Griffon:" + root.getName());
    }
  }

  @Override
  public VirtualFile getSdkRoot(@Nullable Module module) {
    final VirtualFile[] classRoots = ModuleRootManager.getInstance(module).orderEntries().librariesOnly().getClassesRoots();
    for (VirtualFile file : classRoots) {
      if (GriffonLibraryPresentationProvider.isGriffonCoreJar(file)) {
        final VirtualFile localFile = JarFileSystem.getInstance().getVirtualFileForJar(file);
        if (localFile != null) {
          final VirtualFile parent = localFile.getParent();
          if (parent != null) {
            return parent.getParent();
          }
        }
        return null;
      }
    }
    return null;
  }

  @Override
  public String getUserLibraryName() {
    return GRIFFON_USER_LIBRARY;
  }

  @Override
  public JavaParameters createJavaParameters(@NotNull Module module, boolean forCreation, boolean forTests,
                                             boolean classpathFromDependencies, @NotNull String command, @NotNull String... args)
    throws ExecutionException {
    JavaParameters params = new JavaParameters();

    Sdk sdk = ModuleRootManager.getInstance(module).getSdk();

    params.setJdk(sdk);
    final VirtualFile sdkRoot = getSdkRoot(module);
    if (sdkRoot == null) {
      return params;
    }

    Map<String, String> env = params.getEnv();
    if (env == null) {
      env = new HashMap<String, String>();
      params.setEnv(env);
    }
    env.put(getSdkHomePropertyName(), FileUtil.toSystemDependentName(sdkRoot.getPath()));

    final VirtualFile lib = sdkRoot.findChild("lib");
    if (lib != null) {
      for (final VirtualFile child : lib.getChildren()) {
        final String name = child.getName();
        if (name.startsWith("groovy-all-") && name.endsWith(".jar")) {
          params.getClassPath().add(child);
        }
      }
    }
    final VirtualFile dist = sdkRoot.findChild("dist");
    if (dist != null) {
      for (final VirtualFile child : dist.getChildren()) {
        final String name = child.getName();
        if (name.endsWith(".jar")) {
          if (name.startsWith("griffon-cli-") || name.startsWith("griffon-rt-")) {
            params.getClassPath().add(child);
          }
        }
      }
    }


    /////////////////////////////////////////////////////////////

    params.setMainClass("org.codehaus.griffon.cli.support.GriffonStarter");

    final VirtualFile rootFile;

    if (forCreation) {
      VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
      if (roots.length != 1) {
        throw new ExecutionException("Failed to initialize griffon module: module " + module.getName() + " contains more than one root");
      }

      args = new String[]{roots[0].getName()};
      rootFile = roots[0].getParent();
    }
    else {
      rootFile = findAppRoot(module);
      if (rootFile == null) {
        throw new ExecutionException("Failed to run griffon command: module " + module.getName() + " is not a Griffon module");
      }
    }

    String workDir = VfsUtil.virtualToIoFile(rootFile).getAbsolutePath();

    if (!params.getVMParametersList().getParametersString().contains(XMX_JVM_PARAMETER)) {
      params.getVMParametersList().add("-Xmx256M");
    }

    final String griffonHomePath = FileUtil.toSystemDependentName(sdkRoot.getPath());
    params.getVMParametersList().add("-Dgriffon.home=" + griffonHomePath);
    params.getVMParametersList().add("-Dbase.dir=" + workDir);

    assert sdk != null;
    params.getVMParametersList().add("-Dtools.jar=" + ((JavaSdkType)sdk.getSdkType()).getToolsPath(sdk));

    final String confpath = griffonHomePath + GROOVY_STARTER_CONF;
    params.getVMParametersList().add("-Dgroovy.starter.conf=" + confpath);

    params.getProgramParametersList().add("--main");
    params.getProgramParametersList().add("org.codehaus.griffon.cli.GriffonScriptRunner");
    params.getProgramParametersList().add("--conf");
    params.getProgramParametersList().add(confpath);
    if (!forCreation && classpathFromDependencies) {
      final String path = getApplicationClassPath(module).getPathsString();
      if (StringUtil.isNotEmpty(path)) {
        params.getProgramParametersList().add("--classpath");
        params.getProgramParametersList().add(path);
      }
    }

    params.setWorkingDirectory(workDir);

    String argsString = args.length == 0 ? command : command + ' ' + StringUtil.join(args, " ");
    params.getProgramParametersList().add(argsString);

    params.setDefaultCharset(module.getProject());
    
    return params;
  }

  @Override
  public String getFrameworkName() {
    return "Griffon";
  }

  @Override
  public Icon getIcon() {
    return GRIFFON_ICON;
  }

  @Override
  public String getSdkHomePropertyName() {
    return "GRIFFON_HOME";
  }

  @Override
  protected String getCommonPluginSuffix() {
    return GRIFFON_COMMON_PLUGINS;
  }

  @Override
  public String getGlobalPluginsModuleName() {
    return GLOBAL_PLUGINS_MODULE_NAME;
  }

  @Nullable
  public File getDefaultSdkWorkDir(@NotNull Module module) {
    final String version = GriffonLibraryPresentationProvider.getGriffonVersion(module);
    if (version == null) return null;

    return new File(getUserHomeGriffon(), version);
  }

  @Override
  public boolean isSDKLibrary(Library library) {
    return GriffonLibraryPresentationProvider.isGriffonSdk(library.getFiles(OrderRootType.CLASSES));
  }

  @Override
  public MvcProjectStructure createProjectStructure(@NotNull Module module, boolean auxModule) {
    return new GriffonProjectStructure(module, auxModule);
  }

  @Override
  public LibraryKind<?> getLibraryKind() {
    return GriffonLibraryPresentationProvider.GRIFFON_KIND;
  }

  @Override
  public String getSomeFrameworkClass() {
    return "griffon.core.GriffonApplication";
  }

  public static String getUserHomeGriffon() {
    return MvcPathMacros.getSdkWorkDirParent("griffon");
  }

  public static GriffonFramework getInstance() {
    return EP_NAME.findExtension(GriffonFramework.class);
  }

  private static class GriffonProjectStructure extends MvcProjectStructure {
    public GriffonProjectStructure(Module module, final boolean auxModule) {
      super(module, auxModule, getUserHomeGriffon(), GriffonFramework.getInstance().getSdkWorkDir(module));
    }

    @NotNull
    public String getUserLibraryName() {
      return GRIFFON_USER_LIBRARY;
    }

    public String[] getSourceFolders() {
      return new String[]{"griffon-app/controllers", "griffon-app/models", "griffon-app/views", "src/main", "griffon-app/services"};
    }

    public String[] getTestFolders() {
      return new String[]{"test/unit", "test/integration"};
    }

    public String[] getInvalidSourceFolders() {
      return new String[]{"src"};
    }

    @Override
    public String[] getExcludedFolders() {
      return new String[]{"target/classes", "target/test-classes"};
    }
  }
}
