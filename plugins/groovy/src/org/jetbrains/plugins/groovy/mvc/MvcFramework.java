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

package org.jetbrains.plugins.groovy.mvc;

import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.*;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeView;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ArrayUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.config.GroovyLibraryDescription;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.refactoring.GroovyNamesUtil;

import javax.swing.*;
import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author peter
 */
public abstract class MvcFramework {
  protected static final ExtensionPointName<MvcFramework> EP_NAME = ExtensionPointName.create("org.intellij.groovy.mvc.framework");

  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.mvc.MvcFramework");
  public static final Key<Boolean> CREATE_APP_STRUCTURE = Key.create("CREATE_MVC_APP_STRUCTURE");
  public static final Key<Boolean> UPGRADE = Key.create("UPGRADE");
  @NonNls public static final String GROOVY_STARTER_CONF = "/conf/groovy-starter.conf";
  @NonNls public static final String XMX_JVM_PARAMETER = "-Xmx";

  public abstract boolean hasSupport(@NotNull Module module);

  public boolean isAuxModule(@NotNull Module module) {
    return isCommonPluginsModule(module) || isGlobalPluginModule(module);
  }

  public GroovyLibraryDescription createLibraryDescription() {
    return new GroovyLibraryDescription(getSdkHomePropertyName(), getLibraryKind(), getDisplayName());
  }

  public boolean hasFrameworkJar(@NotNull Module module) {
    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
    return JavaPsiFacade.getInstance(module.getProject()).findClass(getSomeFrameworkClass(), scope) != null;
  }

  public boolean isCommonPluginsModule(@NotNull Module module) {
    return module.getName().endsWith(getCommonPluginSuffix());
  }

  public abstract String getApplicationDirectoryName();

  public void syncSdkAndLibrariesInPluginsModule(@NotNull Module module) {
    final Module pluginsModule = findCommonPluginsModule(module);
    if (pluginsModule != null) {
      MvcModuleStructureUtil.syncAuxModuleSdk(module, pluginsModule, this);
    }
  }


  public abstract void upgradeFramework(@NotNull Module module);

  public void createApplicationIfNeeded(@NotNull final Module module) {
    if (findAppRoot(module) == null && module.getUserData(CREATE_APP_STRUCTURE) == Boolean.TRUE) {
      while (ModuleRootManager.getInstance(module).getSdk() == null) {
        if (Messages.showYesNoDialog(module.getProject(), "Cannot generate " + getDisplayName() + " project structure because JDK is not specified for module \"" +
                                                          module.getName() + "\".\n" +
                                                          getDisplayName() + " project will not be created if you don't specify JDK.\nDo you want to specify JDK?",
                                     "Error", Messages.getErrorIcon()) == 1) {
          return;
        }
        ProjectSettingsService.getInstance(module.getProject()).showModuleConfigurationDialog(module.getName(), "Dependencies", false);
      }
      module.putUserData(CREATE_APP_STRUCTURE, null);
      final int result = Messages.showDialog(module.getProject(),
                                             "Create default " + getDisplayName() + " directory structure in module '" + module.getName() + "'?",
                                             "Create " + getDisplayName() + " application", new String[]{"Run 'create-&app'", "Run 'create-&plugin'", "&Cancel"}, 0, getIcon());
      if (result < 0 || result > 1) {
        return;
      }

      final ProcessBuilder pb = createCommandAndShowErrors(null, module, true, result == 0 ? "create-app" : "create-plugin");
      if (pb == null) return;

      MvcConsole.getInstance(module.getProject()).executeProcess(module, pb, new Runnable() {
        public void run() {
          if (module.isDisposed()) return;

          VirtualFile root = findAppRoot(module);
          if (root == null) return;

          PsiDirectory psiDir = PsiManager.getInstance(module.getProject()).findDirectory(root);
          IdeView ide = LangDataKeys.IDE_VIEW.getData(DataManager.getInstance().getDataContext());
          if (ide != null) ide.selectElement(psiDir);

          //also here comes fileCreated(application.properties) which manages roots and run configuration
        }
      }, true);
    }

  }

  public abstract void updateProjectStructure(@NotNull final Module module);

  public abstract void ensureRunConfigurationExists(@NotNull Module module);

  @Nullable
  public VirtualFile findAppRoot(@Nullable Module module) {
    if (module == null) return null;

    String appDirName = getApplicationDirectoryName();

    for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
      if (root.findChild(appDirName) != null) return root;
    }

    return null;
  }

  @Nullable
  public VirtualFile findAppDirectory(@Nullable Module module) {
    if (module == null) return null;

    String appDirName = getApplicationDirectoryName();

    for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
      VirtualFile res = root.findChild(appDirName);
      if (res != null) return res;
    }

    return null;
  }

  @Nullable
  public abstract VirtualFile getSdkRoot(@Nullable Module module);

  public abstract String getUserLibraryName();

  protected List<File> getImplicitClasspathRoots(@NotNull Module module) {
    final List<File> toExclude = new ArrayList<File>();

    VirtualFile sdkRoot = getSdkRoot(module);
    if (sdkRoot != null) toExclude.add(VfsUtil.virtualToIoFile(sdkRoot));

    ContainerUtil.addIfNotNull(getCommonPluginsDir(module), toExclude);
    final VirtualFile appRoot = findAppRoot(module);
    if (appRoot != null) {
      VirtualFile pluginDir = appRoot.findChild(MvcModuleStructureUtil.PLUGINS_DIRECTORY);
      if (pluginDir != null) toExclude.add(VfsUtil.virtualToIoFile(pluginDir));


      VirtualFile libDir = appRoot.findChild("lib");
      if (libDir != null) toExclude.add(VfsUtil.virtualToIoFile(libDir));
    }

    final Library library = MvcModuleStructureUtil.findUserLibrary(module, getUserLibraryName());
    if (library != null) {
      for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
        toExclude.add(VfsUtil.virtualToIoFile(PathUtil.getLocalFile(file)));
      }
    }
    return toExclude;
  }

  private PathsList removeFrameworkStuff(Module module, List<VirtualFile> rootFiles) {
    final List<File> toExclude = getImplicitClasspathRoots(module);
    if (LOG.isDebugEnabled()) {
      LOG.debug("Before removing framework stuff: " + rootFiles);
      LOG.debug("Implicit roots:" + toExclude);
    }

    PathsList scriptClassPath = new PathsList();
    eachRoot:
    for (VirtualFile file : rootFiles) {
      for (final File excluded : toExclude) {
        if (VfsUtil.isAncestor(excluded, VfsUtil.virtualToIoFile(file), false)) {
          continue eachRoot;
        }
      }
      scriptClassPath.add(file);
    }
    return scriptClassPath;
  }

  public PathsList getApplicationClassPath(Module module) {
    final List<VirtualFile> classPath = OrderEnumerator.orderEntries(module).recursively().withoutSdk().getPathsList().getVirtualFiles();

    retainOnlyJarsAndDirectories(classPath);

    removeModuleOutput(module, classPath);

    final Module pluginsModule = findCommonPluginsModule(module);
    if (pluginsModule != null) {
      removeModuleOutput(pluginsModule, classPath);
    }

    return removeFrameworkStuff(module, classPath);
  }

  public abstract boolean updatesWholeProject();

  private static void retainOnlyJarsAndDirectories(List<VirtualFile> woSdk) {
    for (Iterator<VirtualFile> iterator = woSdk.iterator(); iterator.hasNext();) {
      VirtualFile file = iterator.next();
      final VirtualFile local = JarFileSystem.getInstance().getVirtualFileForJar(file);
      final boolean dir = file.isDirectory();
      final String name = file.getName();
      if (LOG.isDebugEnabled()) {
        LOG.debug("Considering: " + file.getPath() + "; local=" + local + "; dir=" + dir + "; name=" + name);
      }
      if (dir || local != null) {
        continue;
      }
      if (name.endsWith(".jar")) {
        continue;
      }
      LOG.debug("Removing");
      iterator.remove();
    }
  }

  private static void removeModuleOutput(Module module, List<VirtualFile> from) {
    final CompilerModuleExtension extension = ModuleRootManager.getInstance(module).getModuleExtension(CompilerModuleExtension.class);
    from.remove(extension.getCompilerOutputPath());
    from.remove(extension.getCompilerOutputPathForTests());
  }


  public static Pair<String, String[]> parsedCmd(String cmdLine) {
    return parsedCmd(ParametersList.parse(cmdLine));
  }

  public static Pair<String, String[]> parsedCmd(String[] args) {
    if (args.length == 0) {
      return new Pair<String, String[]>("", ArrayUtil.EMPTY_STRING_ARRAY);
    }
    if (args.length == 1) {
      return new Pair<String, String[]>(args[0], ArrayUtil.EMPTY_STRING_ARRAY);
    }

    String[] array = new String[args.length - 1];
    System.arraycopy(args, 1, array, 0, array.length);

    return new Pair<String, String[]>(args[0], array);
  }

  public abstract JavaParameters createJavaParameters(@NotNull Module module,
                                                      boolean forCreation,
                                                      boolean forTests,
                                                      boolean classpathFromDependencies,
                                                      @NotNull String command,
                                                      @NotNull String... args) throws ExecutionException;

  protected static void ensureRunConfigurationExists(Module module, ConfigurationType configurationType, String name) {
    final RunManagerEx runManager = RunManagerEx.getInstanceEx(module.getProject());
    for (final RunConfiguration runConfiguration : runManager.getConfigurations(configurationType)) {
      if (runConfiguration instanceof MvcRunConfiguration && ((MvcRunConfiguration)runConfiguration).getModule() == module) {
        return;
      }
    }

    final ConfigurationFactory factory = configurationType.getConfigurationFactories()[0];
    final RunnerAndConfigurationSettings runSettings = runManager.createRunConfiguration(name,
                                                                                                                                 factory);
    final MvcRunConfiguration configuration = (MvcRunConfiguration)runSettings.getConfiguration();
    configuration.setModule(module);
    runManager.addConfiguration(runSettings, false);
    runManager.setActiveConfiguration(runSettings);

    final CompileStepBeforeRun.MakeBeforeRunTask runTask = runManager.getBeforeRunTask(configuration, CompileStepBeforeRun.ID);
    if (runTask != null) {
      runTask.setEnabled(false);
    }
  }

  public abstract String getFrameworkName();
  public String getDisplayName() {
    return getFrameworkName();
  }
  public abstract Icon getIcon();

  public abstract String getSdkHomePropertyName();

  @Nullable
  public ProcessBuilder createCommandAndShowErrors(@NotNull Module module, @NotNull String command, @NotNull String... args) {
    return createCommandAndShowErrors(null, module, command, args);
  }

  @Nullable
  public ProcessBuilder createCommandAndShowErrors(@Nullable String vmOptions, @NotNull Module module, @NotNull String command, @NotNull String... args) {
    return createCommandAndShowErrors(vmOptions, module, false, command, args);
  }

  @Nullable
  public ProcessBuilder createCommandAndShowErrors(@Nullable String vmOptions, @NotNull Module module, final boolean forCreation, @NotNull String command, @NotNull String... args) {
    try {
      return createCommand(module, vmOptions, forCreation, command, args);
    }
    catch (ExecutionException e) {
      Messages.showErrorDialog(e.getMessage(), "Failed to run grails command: " + command);
      return null;
    }
  }

  @NotNull
  public ProcessBuilder createCommand(@NotNull Module module, @Nullable String vmOptions, final boolean forCreation, @NotNull String command, @NotNull String... args)
    throws ExecutionException {
    final JavaParameters params = createJavaParameters(module, forCreation, false, true, command, args);

    addJavaHome(params, module);

    if (!StringUtil.isEmpty(vmOptions)) {
      params.getVMParametersList().addParametersString(vmOptions);
    }

    final ProcessBuilder builder = createProcessBuilder(params);
    final VirtualFile griffonHome = getSdkRoot(module);
    if (griffonHome != null) {
      builder.environment().put(getSdkHomePropertyName(), FileUtil.toSystemDependentName(griffonHome.getPath()));
    }

    final VirtualFile root = findAppRoot(module);
    final File ioRoot = root != null ? VfsUtil.virtualToIoFile(root) : new File(module.getModuleFilePath()).getParentFile();
    builder.directory(forCreation ? ioRoot.getParentFile() : ioRoot);

    return builder;
  }

  public static void addJavaHome(@NotNull JavaParameters params, @NotNull Module module) {
    final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk != null && sdk.getSdkType() instanceof JavaSdkType) {
      String path = StringUtil.trimEnd(sdk.getHomePath(), File.separator);
      if (StringUtil.isNotEmpty(path)) {
        Map<String, String> env = params.getEnv();
        if (env == null) {
          env = new HashMap<String, String>();
          params.setEnv(env);
        }
        
        env.put("JAVA_HOME", FileUtil.toSystemDependentName(path));
      }
    }
  }

  public static ProcessBuilder createProcessBuilder(JavaParameters params) {
    try {
      ProcessBuilder builder = new ProcessBuilder(CommandLineBuilder.createFromJavaParameters(params).getCommands());
      Map<String, String> env = params.getEnv();
      if (env != null) {
        builder.environment().putAll(env);
      }

      String workDir = params.getWorkingDirectory();
      if (workDir != null) {
        builder.directory(new File(workDir));
      }

      return builder;
    }
    catch (CantRunException e) {
      throw new RuntimeException(e);
    }
  }

  private static void extractPlugins(@Nullable VirtualFile pluginRoot, Map<String, VirtualFile> res) {
    if (pluginRoot != null) {
      VirtualFile[] children = pluginRoot.getChildren();
      if (children != null) {
        for (VirtualFile child : children) {
          if (child.isDirectory()) {
            res.put(child.getName(), child);
          }
        }
      }
    }
  }

  public Collection<VirtualFile> getAllPluginRoots(@NotNull Module module, boolean refresh) {
    return getCommonPluginRoots(module, refresh);
  }

  protected void collectCommonPluginRoots(Map<String, VirtualFile> result, @NotNull Module module, boolean refresh) {
    if (isCommonPluginsModule(module)) {
      String appDirName = getApplicationDirectoryName();

      for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
        if (root.findChild(appDirName) != null) {
          result.put(root.getName(), root);
        }
      }
    }
    else {
      VirtualFile root = findAppRoot(module);
      if (root == null) return;

      extractPlugins(root.findChild(MvcModuleStructureUtil.PLUGINS_DIRECTORY), result);
      extractPlugins(MvcModuleStructureUtil.findFile(getCommonPluginsDir(module), refresh), result);
      extractPlugins(MvcModuleStructureUtil.findFile(getGlobalPluginsDir(module), refresh), result);
    }
  }

  public Collection<VirtualFile> getCommonPluginRoots(@NotNull Module module, boolean refresh) {
    Map<String, VirtualFile> result = new HashMap<String, VirtualFile>();
    collectCommonPluginRoots(result, module, refresh);
    return result.values();
  }

  @Nullable
  public Module findCommonPluginsModule(@NotNull Module module) {
    return ModuleManager.getInstance(module.getProject()).findModuleByName(getCommonPluginsModuleName(module));
  }

  public boolean isGlobalPluginModule(@NotNull Module module) {
    return module.getName().startsWith(getGlobalPluginsModuleName());
  }

  @Nullable
  public File getSdkWorkDir(@NotNull Module module) {
    return getDefaultSdkWorkDir(module);
  }

  @Nullable
  public abstract File getDefaultSdkWorkDir(@NotNull Module module);

  @Nullable
  public File getGlobalPluginsDir(@NotNull Module module) {
    final File sdkWorkDir = getSdkWorkDir(module);
    return sdkWorkDir == null ? null : new File(sdkWorkDir, "global-plugins");
  }

  @Nullable
  public File getCommonPluginsDir(@NotNull Module module) {
    final File grailsWorkDir = getSdkWorkDir(module);
    if (grailsWorkDir == null) return null;

    final VirtualFile root = findAppRoot(module);
    if (root == null) return null;

    return new File(grailsWorkDir, "projects/" + root.getName() + "/plugins");
  }

  public Map<String, String> getInstalledPluginVersions(@NotNull Module module) {
    final PropertiesFile properties = MvcModuleStructureUtil.findApplicationProperties(module, this);
    if (properties == null) {
      return Collections.emptyMap();
    }

    Map<String, String> pluginNames = new HashMap<String, String>();

    for (final Property property : properties.getProperties()) {
      String propName = property.getName();
      if (propName != null) {
        propName = propName.trim();
        if (propName.startsWith("plugins.")) {
          String pluginName = propName.substring("plugins.".length());
          String pluginVersion = property.getValue();
          if (pluginName.length() > 0 && pluginVersion != null) {
            pluginVersion = pluginVersion.trim();
            if (pluginVersion.length() > 0) {
              pluginNames.put(pluginName, pluginVersion);
            }
          }
        }
      }
    }

    return pluginNames;
  }

  protected abstract String getCommonPluginSuffix();

  public abstract String getGlobalPluginsModuleName();

  public String getCommonPluginsModuleName(Module module) {
    return module.getName() + getCommonPluginSuffix();
  }

  public abstract boolean isSDKLibrary(Library library);

  public abstract MvcProjectStructure createProjectStructure(@NotNull Module module, boolean auxModule);

  public abstract LibraryKind<?> getLibraryKind();

  public abstract String getSomeFrameworkClass();

  public static void addAvailableSystemScripts(final Collection<String> result, @NotNull Module module) {
    VirtualFile scriptRoot = null;

    GlobalSearchScope searchScope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);

    for (PsiClass aClass : JavaPsiFacade.getInstance(module.getProject()).findClasses("CreateApp_", searchScope)) {
      PsiClass superClass = aClass.getSuperClass();
      if (superClass != null && GroovyCommonClassNames.GROOVY_LANG_SCRIPT.equals(superClass.getQualifiedName())) {
        PsiFile psiFile = aClass.getContainingFile();
        if (psiFile != null) {
          VirtualFile file = psiFile.getVirtualFile();
          if (file != null && file.getFileSystem() instanceof JarFileSystem) {
            VirtualFile parent = file.getParent();
            if (parent != null && parent.findChild("Bootstrap.class") != null) {
              scriptRoot = parent;
              break;
            }
          }
        }
      }
    }

    if (scriptRoot == null) return;

    Pattern scriptPattern = Pattern.compile("([A-Za-z0-9]+)_?\\.class");

    for (VirtualFile file : scriptRoot.getChildren()) {
      Matcher matcher = scriptPattern.matcher(file.getName());
      if (matcher.matches()) {
        result.add(GroovyNamesUtil.camelToSnake(matcher.group(1)));
      }
    }

  }

  public abstract boolean isToReformatOnCreation(VirtualFile file);

  public static void addAvailableScripts(final Collection<String> result, @Nullable final VirtualFile root) {
    if (root == null || !root.isDirectory()) {
      return;
    }

    final VirtualFile scripts = root.findChild("scripts");

    if (scripts == null || !scripts.isDirectory()) {
      return;
    }

    for (VirtualFile child : scripts.getChildren()) {
      if (isScriptFile(child)) {
        result.add(GroovyNamesUtil.camelToSnake(child.getNameWithoutExtension()));
      }
    }
  }

  @Nullable
  public static MvcFramework findCommonPluginModuleFramework(Module module) {
    for (MvcFramework framework : EP_NAME.getExtensions()) {
      if (framework.isCommonPluginsModule(module)) {
        return framework;
      }
    }
    return null;
  }

  public static boolean isScriptFileName(String fileName) {
    return fileName.endsWith(GroovyFileType.DEFAULT_EXTENSION) && fileName.charAt(0) != '_';
  }

  private static boolean isScriptFile(VirtualFile virtualFile) {
    return !virtualFile.isDirectory() && isScriptFileName(virtualFile.getName());
  }
}
