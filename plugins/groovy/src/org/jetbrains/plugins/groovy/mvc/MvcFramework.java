/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.mvc;

import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.compiler.options.CompileStepBeforeRunNoErrorCheck;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeView;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.roots.ui.configuration.libraries.AddCustomLibraryDialog;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.PathsList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.config.GroovyLibraryDescription;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

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

  public boolean hasFrameworkStructure(@NotNull Module module) {
    VirtualFile appDir = findAppDirectory(module);
    if (appDir == null) return false;

    return appDir.findChild("controllers") != null && appDir.findChild("conf") != null;
  }

  public boolean hasFrameworkJar(@NotNull Module module) {
    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, false);
    return JavaPsiFacade.getInstance(module.getProject()).findClass(getSomeFrameworkClass(), scope) != null;
  }

  @NotNull
  public Map<String, Runnable> createConfigureActions(final @NotNull Module module) {
    return Collections.singletonMap("Configure " + getFrameworkName() + " SDK",
                                    () -> configureAsLibraryDependency(module));
  }

  protected void configureAsLibraryDependency(@NotNull Module module) {
    final GroovyLibraryDescription description = createLibraryDescription();
    final AddCustomLibraryDialog dialog = AddCustomLibraryDialog.createDialog(description, module, null);
    dialog.setTitle("Change " + getDisplayName() + " SDK version");
    if (dialog.showAndGet()) module.putUserData(UPGRADE, Boolean.TRUE);
  }

  public boolean isCommonPluginsModule(@NotNull Module module) {
    return module.getName().endsWith(getCommonPluginSuffix());
  }

  public List<Module> reorderModulesForMvcView(List<Module> modules) {
    return modules;
  }

  @NonNls
  @NotNull
  public abstract String getApplicationDirectoryName();

  public void syncSdkAndLibrariesInPluginsModule(@NotNull Module module) {
    final Module pluginsModule = findCommonPluginsModule(module);
    if (pluginsModule != null) {
      MvcModuleStructureUtil.syncAuxModuleSdk(module, pluginsModule, this);
    }
  }

  public boolean isInteractiveConsoleSupported(@NotNull Module module) {
    return false;
  }

  public void runInteractiveConsole(@NotNull Module module) {
    throw new UnsupportedOperationException();
  }

  public abstract void upgradeFramework(@NotNull Module module);

  public void createApplicationIfNeeded(@NotNull final Module module) {
    if (findAppRoot(module) == null && module.getUserData(CREATE_APP_STRUCTURE) == Boolean.TRUE) {
      while (ModuleRootManager.getInstance(module).getSdk() == null) {
        if (Messages.showYesNoDialog(module.getProject(), "Cannot generate " + getDisplayName() + " project structure because JDK is not specified for module \"" +
                                                          module.getName() + "\".\n" +
                                                          getDisplayName() + " project will not be created if you don't specify JDK.\nDo you want to specify JDK?",
                                     "Error", Messages.getErrorIcon()) == Messages.NO) {
          return;
        }
        ProjectSettingsService.getInstance(module.getProject()).showModuleConfigurationDialog(module.getName(), ClasspathEditor.NAME);
      }
      module.putUserData(CREATE_APP_STRUCTURE, null);
      final GeneralCommandLine commandLine = getCreationCommandLine(module);
      if (commandLine == null) return;

      MvcConsole.executeProcess(module, commandLine, () -> {
        VirtualFile root = findAppRoot(module);
        if (root == null) return;

        PsiDirectory psiDir = PsiManager.getInstance(module.getProject()).findDirectory(root);
        IdeView ide = LangDataKeys.IDE_VIEW.getData(DataManager.getInstance().getDataContext());
        if (ide != null) ide.selectElement(psiDir);

        //also here comes fileCreated(application.properties) which manages roots and run configuration
      }, true);
    }

  }

  @Nullable
  protected GeneralCommandLine getCreationCommandLine(Module module) {
    return null;
  }

  public abstract void updateProjectStructure(@NotNull final Module module);

  public void ensureRunConfigurationExists(@NotNull Module module) {}

  @Nullable
  public VirtualFile findAppRoot(@Nullable Module module) {
    if (module == null) return null;

    String appDirName = getApplicationDirectoryName();

    for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
      if (root.isInLocalFileSystem() && root.findChild(appDirName) != null) return root;
    }

    return null;
  }

  @Nullable
  public VirtualFile findAppRoot(@Nullable PsiElement element) {
    VirtualFile appDirectory = findAppDirectory(element);
    return appDirectory == null ? null : appDirectory.getParent();
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
  public VirtualFile findAppDirectory(@Nullable PsiElement element) {
    if (element == null) return null;

    PsiFile containingFile = element.getContainingFile().getOriginalFile();
    VirtualFile file = containingFile.getVirtualFile();
    if (file == null) return null;

    ProjectFileIndex index = ProjectRootManager.getInstance(containingFile.getProject()).getFileIndex();

    VirtualFile root = index.getContentRootForFile(file);
    if (root == null) return null;

    return root.findChild(getApplicationDirectoryName());
  }

  @Nullable
  public abstract VirtualFile getSdkRoot(@Nullable Module module);

  public abstract String getUserLibraryName();

  protected abstract boolean isCoreJar(@NotNull VirtualFile localFile);

  protected List<File> getImplicitClasspathRoots(@NotNull Module module) {
    final List<File> toExclude = new ArrayList<>();

    VirtualFile sdkRoot = getSdkRoot(module);
    if (sdkRoot != null) toExclude.add(VfsUtilCore.virtualToIoFile(sdkRoot));

    ContainerUtil.addIfNotNull(toExclude, getCommonPluginsDir(module));
    final VirtualFile appRoot = findAppRoot(module);
    if (appRoot != null) {
      VirtualFile pluginDir = appRoot.findChild(MvcModuleStructureUtil.PLUGINS_DIRECTORY);
      if (pluginDir != null) toExclude.add(VfsUtilCore.virtualToIoFile(pluginDir));


      VirtualFile libDir = appRoot.findChild("lib");
      if (libDir != null) toExclude.add(VfsUtilCore.virtualToIoFile(libDir));
    }

    final Library library = MvcModuleStructureUtil.findUserLibrary(module, getUserLibraryName());
    if (library != null) {
      for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
        toExclude.add(VfsUtilCore.virtualToIoFile(VfsUtil.getLocalFile(file)));
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
        if (VfsUtilCore.isAncestor(excluded, VfsUtilCore.virtualToIoFile(file), false)) {
          continue eachRoot;
        }
      }
      scriptClassPath.add(file);
    }
    return scriptClassPath;
  }

  public PathsList getApplicationClassPath(Module module) {
    final List<VirtualFile> classPath = ContainerUtil.newArrayList();
    classPath.addAll(OrderEnumerator.orderEntries(module).recursively().withoutSdk().getPathsList().getVirtualFiles());

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

  public abstract JavaParameters createJavaParameters(@NotNull Module module,
                                                      boolean forCreation,
                                                      boolean forTests,
                                                      boolean classpathFromDependencies,
                                                      @NotNull MvcCommand command) throws ExecutionException;

  protected static void ensureRunConfigurationExists(Module module, ConfigurationType configurationType, String name) {
    final RunManager runManager = RunManager.getInstance(module.getProject());
    for (final RunConfiguration runConfiguration : runManager.getConfigurationsList(configurationType)) {
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
    runManager.setSelectedConfiguration(runSettings);

    RunManagerEx.disableTasks(module.getProject(), configuration, CompileStepBeforeRun.ID, CompileStepBeforeRunNoErrorCheck.ID);
  }

  @NonNls
  @NotNull
  public abstract String getFrameworkName();

  public String getDisplayName() {
    return getFrameworkName();
  }

  public abstract Icon getIcon(); // 16*16

  public abstract Icon getToolWindowIcon(); // 13*13

  public abstract String getSdkHomePropertyName();

  @Nullable
  public GeneralCommandLine createCommandAndShowErrors(@NotNull Module module, @NotNull String command, String... args) {
    return createCommandAndShowErrors(module, new MvcCommand(command, args));
  }

  @Nullable
  public GeneralCommandLine createCommandAndShowErrors(@NotNull Module module, @NotNull MvcCommand command) {
    return createCommandAndShowErrors(module, false, command);
  }

  @Nullable
  public GeneralCommandLine createCommandAndShowErrors(@NotNull Module module, final boolean forCreation, @NotNull MvcCommand command) {
    try {
      return createCommand(module, forCreation, command);
    }
    catch (ExecutionException e) {
      Messages.showErrorDialog(e.getMessage(), "Failed to run grails command: " + command);
      return null;
    }
  }

  @NotNull
  public GeneralCommandLine createCommand(@NotNull Module module,
                                          boolean forCreation,
                                          @NotNull MvcCommand command) throws ExecutionException {
    final JavaParameters params = createJavaParameters(module, forCreation, false, true, command);
    addJavaHome(params, module);

    final GeneralCommandLine commandLine = createCommandLine(params);

    final VirtualFile griffonHome = getSdkRoot(module);
    if (griffonHome != null) {
      commandLine.getEnvironment().put(getSdkHomePropertyName(), FileUtil.toSystemDependentName(griffonHome.getPath()));
    }

    final VirtualFile root = findAppRoot(module);
    final File ioRoot = root != null ? VfsUtilCore.virtualToIoFile(root) : new File(module.getModuleFilePath()).getParentFile();
    commandLine.setWorkDirectory(forCreation ? ioRoot.getParentFile() : ioRoot);

    return commandLine;
  }

  public static void addJavaHome(@NotNull JavaParameters params, @NotNull Module module) {
    final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk != null && sdk.getSdkType() instanceof JavaSdkType) {
      addJavaHome(sdk, params);
    }
  }

  public static void addJavaHome(Sdk sdk, @NotNull JavaParameters params) {
    String homePath = sdk.getHomePath();
    if (homePath != null) {
      String path = StringUtil.trimEnd(homePath, File.separator);
      if (StringUtil.isNotEmpty(path)) {
        params.addEnv("JAVA_HOME", FileUtil.toSystemDependentName(path));
      }
    }
  }

  public static GeneralCommandLine createCommandLine(@NotNull JavaParameters params) throws CantRunException {
    return params.toCommandLine();
  }

  private void extractPlugins(Project project, @Nullable VirtualFile pluginRoot, boolean refreshPluginRoot, Map<String, VirtualFile> res) {
    if (pluginRoot != null) {
      if (refreshPluginRoot) {
        pluginRoot.refresh(false, false);
      }

      VirtualFile[] children = pluginRoot.getChildren();
      if (children != null) {
        for (VirtualFile child : children) {
          String pluginName = getInstalledPluginNameByPath(project, child);
          if (pluginName != null) {
            res.put(pluginName, child);
          }
        }
      }
    }
  }

  public Collection<VirtualFile> getAllPluginRoots(@NotNull Module module, boolean refresh) {
    return getCommonPluginRoots(module, refresh);
  }

  public void collectCommonPluginRoots(Map<String, VirtualFile> result, @NotNull Module module, boolean refresh) {
    if (isCommonPluginsModule(module)) {
      for (VirtualFile root : ModuleRootManager.getInstance(module).getContentRoots()) {
        String pluginName = getInstalledPluginNameByPath(module.getProject(), root);
        if (pluginName != null) {
          result.put(pluginName, root);
        }
      }
    }
    else {
      VirtualFile root = findAppRoot(module);
      if (root == null) return;

      extractPlugins(module.getProject(), root.findChild(MvcModuleStructureUtil.PLUGINS_DIRECTORY), refresh, result);
      extractPlugins(module.getProject(), MvcModuleStructureUtil.findFile(getCommonPluginsDir(module), refresh), refresh, result);
      extractPlugins(module.getProject(), MvcModuleStructureUtil.findFile(getGlobalPluginsDir(module), refresh), refresh, result);
    }
  }

  public Collection<VirtualFile> getCommonPluginRoots(@NotNull Module module, boolean refresh) {
    Map<String, VirtualFile> result = new HashMap<>();
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

    final String applicationName = getApplicationName(module);
    if (applicationName == null) return null;

    return new File(grailsWorkDir, "projects/" + applicationName + "/plugins");
  }

  public String getApplicationName(Module module) {
    final VirtualFile root = findAppRoot(module);
    if (root == null) return null;
    return root.getName();
  }

  protected abstract String getCommonPluginSuffix();

  public abstract String getGlobalPluginsModuleName();

  public String getCommonPluginsModuleName(Module module) {
    return module.getName() + getCommonPluginSuffix();
  }

  public abstract boolean isSDKLibrary(Library library);

  public abstract MvcProjectStructure createProjectStructure(@NotNull Module module, boolean auxModule);

  public abstract LibraryKind getLibraryKind();

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
            if (parent != null && parent.findChild("Console.class") != null) {
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

  @Nullable
  public String getInstalledPluginNameByPath(Project project, @NotNull VirtualFile pluginPath) {
    VirtualFile pluginXml = pluginPath.findChild("plugin.xml");
    if (pluginXml == null) return null;

    PsiFile pluginXmlPsi = PsiManager.getInstance(project).findFile(pluginXml);
    if (!(pluginXmlPsi instanceof XmlFile)) return null;

    XmlTag rootTag = ((XmlFile)pluginXmlPsi).getRootTag();
    if (rootTag == null || !"plugin".equals(rootTag.getName())) return null;

    XmlAttribute attrName = rootTag.getAttribute("name");
    if (attrName == null) return null;

    String res = attrName.getValue();
    if (res == null) return null;

    res = res.trim();
    if (res.isEmpty()) return null;

    return res;
  }

  public boolean isUpgradeActionSupported(Module module) {
    return true;
  }

  public boolean isRunTargetActionSupported(Module module) { return false; }

  @Nullable
  public static MvcFramework getInstance(@Nullable final Module module) {
    if (module == null) {
      return null;
    }

    final Project project = module.getProject();

    return CachedValuesManager.getManager(project).getCachedValue(module, () -> {
      final ModificationTracker tracker = MvcModuleStructureSynchronizer.getInstance(project).getFileAndRootsModificationTracker();
      for (final MvcFramework framework : EP_NAME.getExtensions()) {
        if (framework.hasSupport(module)) {
          return CachedValueProvider.Result.create(framework, tracker);
        }
      }
      return CachedValueProvider.Result.create(null, tracker);

    });
  }

  @Nullable
  public static MvcFramework getInstanceBySdk(@NotNull Module module) {
    for (final MvcFramework framework : EP_NAME.getExtensions()) {
      if (framework.getSdkRoot(module) != null) {
        return framework;
      }
    }
    return null;
  }

  public boolean showActionGroup() {
    return true;
  }
}
