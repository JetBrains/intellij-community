// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run;

import com.intellij.diagnostic.logging.LogConfigurationPanel;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.JetBrainsProtocolHandler;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.options.SettingsEditorGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.JavaModuleOptions;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformUtils;
import com.intellij.util.SlowOperations;
import com.intellij.util.system.OS;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;
import org.jetbrains.idea.devkit.projectRoots.IntelliJPlatformProduct;
import org.jetbrains.idea.devkit.projectRoots.Sandbox;
import org.jetbrains.idea.devkit.util.DescriptorUtil;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static com.intellij.idea.LoggerFactory.LOG_FILE_NAME;

public class PluginRunConfiguration extends RunConfigurationBase<Element> implements ModuleRunConfiguration {
  private static final String NAME = "name";
  private static final String MODULE = "module";
  private static final String ALTERNATIVE_PATH_ELEMENT = "alternative-path";
  private static final String PATH = "path";
  private static final String ALTERNATIVE_PATH_ENABLED_ATTR = "alternative-path-enabled";

  private Module myModule;
  private String myModuleName;

  public String VM_PARAMETERS;
  public String PROGRAM_PARAMETERS;

  private String ALTERNATIVE_JRE_PATH = null;
  private boolean ALTERNATIVE_JRE_PATH_ENABLED = false;

  public PluginRunConfiguration(Project project, ConfigurationFactory factory, String name) {
    super(project, factory, name);
    addPredefinedLogFile(new PredefinedLogFile(LOG_FILE_NAME, true));
  }

  @Override
  public @Nullable LogFileOptions getOptionsForPredefinedLogFile(PredefinedLogFile predefinedLogFile) {
    if (LOG_FILE_NAME.equals(predefinedLogFile.getId())) {
      final Module module = getModule();
      final Sdk ideaJdk = module != null ? IdeaJdk.findIdeaJdk(ModuleRootManager.getInstance(module).getSdk()) : null;
      if (ideaJdk != null) {
        final String sandboxHome = ((Sandbox)ideaJdk.getSdkAdditionalData()).getSandboxHome();
        if (sandboxHome != null) {
          return new LogFileOptions(LOG_FILE_NAME, sandboxHome + "/system/log/" + LOG_FILE_NAME, predefinedLogFile.isEnabled());
        }
      }
    }
    return super.getOptionsForPredefinedLogFile(predefinedLogFile);
  }

  @Override
  public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    SettingsEditorGroup<PluginRunConfiguration> group = new SettingsEditorGroup<>();
    group.addEditor(ExecutionBundle.message("run.configuration.configuration.tab.title"), new PluginRunConfigurationEditor(this));
    group.addEditor(ExecutionBundle.message("logs.tab.title"), new LogConfigurationPanel<>());
    return group;
  }

  @Override
  public RunProfileState getState(final @NotNull Executor executor, final @NotNull ExecutionEnvironment env) throws ExecutionException {
    final Module module = getModule();
    if (module == null) {
      throw new ExecutionException(DevKitBundle.message("run.configuration.no.module.specified"));
    }
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    final Sdk jdk = rootManager.getSdk();
    if (jdk == null) {
      throw CantRunException.noJdkForModule(module);
    }

    final Sdk ideaJdk = IdeaJdk.findIdeaJdk(jdk);
    if (ideaJdk == null) {
      throw new ExecutionException(DevKitBundle.message("sdk.type.incorrect.common"));
    }
    String sandboxHome = ((Sandbox)ideaJdk.getSdkAdditionalData()).getSandboxHome();

    if (sandboxHome == null) {
      throw new ExecutionException(DevKitBundle.message("sandbox.no.configured"));
    }

    try {
      sandboxHome = new File(sandboxHome).getCanonicalPath();
    }
    catch (IOException e) {
      throw new ExecutionException(DevKitBundle.message("sandbox.no.configured"));
    }
    final String canonicalSandbox = sandboxHome;

    //copy license from running instance of idea
    IdeaLicenseHelper.copyIDEALicense(sandboxHome);

    return new JavaCommandLineState(env) {
      @Override
      protected JavaParameters createJavaParameters() throws ExecutionException {

        final JavaParameters params = new JavaParameters();

        ParametersList vm = params.getVMParametersList();

        fillParameterList(vm, VM_PARAMETERS);
        fillParameterList(params.getProgramParametersList(), PROGRAM_PARAMETERS);
        Sdk usedIdeaJdk = ideaJdk;
        String alternativeIdePath = getAlternativeJrePath();
        if (isAlternativeJreEnabled() && !StringUtil.isEmptyOrSpaces(alternativeIdePath)) {
          final Sdk configuredJdk = ProjectJdkTable.getInstance().findJdk(alternativeIdePath);
          if (configuredJdk != null) {
            usedIdeaJdk = configuredJdk;
          }
          else {
            try {
              usedIdeaJdk = (Sdk)usedIdeaJdk.clone();
            }
            catch (CloneNotSupportedException e) {
              throw new ExecutionException(e.getMessage());
            }
            final SdkModificator sdkToSetUp = usedIdeaJdk.getSdkModificator();
            sdkToSetUp.setHomePath(alternativeIdePath);
            sdkToSetUp.commitChanges();
          }
        }
        String ideaJdkHome = usedIdeaJdk.getHomePath();
        boolean fromIdeaProject = PsiUtil.isPathToIntelliJIdeaSources(ideaJdkHome);

        if (!fromIdeaProject) {
          String bootPath = "/lib/boot.jar";
          String bootJarPath = ideaJdkHome + FileUtil.toSystemDependentName(bootPath);
          if (new File(bootJarPath).exists()) {
            //there is no need to add boot.jar in modern IDE builds (181.*)
            vm.add("-Xbootclasspath/a:" + bootJarPath);
          }
        }

        vm.defineProperty(PathManager.PROPERTY_CONFIG_PATH, canonicalSandbox + File.separator + "config");
        vm.defineProperty(PathManager.PROPERTY_SYSTEM_PATH, canonicalSandbox + File.separator + "system");
        vm.defineProperty(PathManager.PROPERTY_PLUGINS_PATH, canonicalSandbox + File.separator + "plugins");
        vm.defineProperty("idea.classpath.index.enabled", "false");

        if (!vm.hasProperty("jdk.module.illegalAccess.silent")) {
          vm.defineProperty("jdk.module.illegalAccess.silent", "true");
        }

        String buildNumber = IdeaJdk.getBuildNumber(ideaJdkHome);
        if (buildNumber != null) {
          String versionString = StringUtil.substringAfter(buildNumber, "-");
          if (versionString != null) {
            Version version = Version.parseVersion(versionString);
            if (version != null && version.isOrGreaterThan(221)) {
              vm.defineProperty(JUnitDevKitPatcher.SYSTEM_CL_PROPERTY, "com.intellij.util.lang.PathClassLoader");
            }
          }
        }

        Sdk internalJavaSdk = ObjectUtils.chooseNotNull(IdeaJdk.getInternalJavaSdk(usedIdeaJdk), usedIdeaJdk);
        var sdkVersion = ((JavaSdk)internalJavaSdk.getSdkType()).getVersion(jdk);
        if (sdkVersion != null && sdkVersion.isAtLeast(JavaSdkVersion.JDK_17)) {
          try (InputStream stream = PluginRunConfiguration.class.getResourceAsStream("OpenedPackages.txt")) {
            assert stream != null;
            JavaModuleOptions.readOptions(stream, OS.CURRENT).forEach(vm::add);
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }

        if (!vm.hasProperty(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY) && PluginModuleType.isOfType(module)) {
          final String id = getPluginId(module);
          if (id != null) {
            vm.defineProperty(JetBrainsProtocolHandler.REQUIRED_PLUGINS_KEY, id);
          }
        }

        if (SystemInfo.isMac) {
          vm.defineProperty("apple.laf.useScreenMenuBar", "true");
          vm.defineProperty("apple.awt.fileDialogForDirectories", "true");
        }

        if (SystemInfo.isXWindow) {
          if (VM_PARAMETERS == null || !VM_PARAMETERS.contains("-Dsun.awt.disablegrab")) {
            vm.defineProperty("sun.awt.disablegrab", "true"); // See http://devnet.jetbrains.net/docs/DOC-1142
          }
        }

        if (!vm.hasProperty(PlatformUtils.PLATFORM_PREFIX_KEY)) {
          if (buildNumber != null) {
            String prefix = IntelliJPlatformProduct.fromBuildNumber(buildNumber).getPlatformPrefix();
            if (prefix != null) {
              vm.defineProperty(PlatformUtils.PLATFORM_PREFIX_KEY, prefix);
            }
          }
        }

        if (!vm.hasProperty(SlowOperations.IDEA_PLUGIN_SANDBOX_MODE)) {
          vm.defineProperty(SlowOperations.IDEA_PLUGIN_SANDBOX_MODE, "true");
        }

        params.setWorkingDirectory(ideaJdkHome + File.separator + "bin" + File.separator);

        params.setJdk(usedIdeaJdk);

        if (fromIdeaProject) {
          OrderEnumerator enumerator = OrderEnumerator.orderEntries(module).productionOnly().recursively();
          for (VirtualFile file : enumerator.getAllLibrariesAndSdkClassesRoots()) {
            params.getClassPath().add(file);
          }
        }
        else {
          final List<String> jars = List.of(
            // log4j, jdom and trove4j needed for running on branch 202 and older
            "log4j.jar", "jdom.jar", "trove4j.jar",
            "openapi.jar", "util.jar", "util_rt.jar", "bootstrap.jar", "idea_rt.jar", "idea.jar",
            "3rd-party-rt.jar", "jna.jar");
          for (String path : jars) {
            params.getClassPath().add(ideaJdkHome + FileUtil.toSystemDependentName("/lib/" + path));
          }
        }
        params.getClassPath().addFirst(((JavaSdkType)usedIdeaJdk.getSdkType()).getToolsPath(usedIdeaJdk));

        params.setMainClass("com.intellij.idea.Main");

        return params;
      }
    };
  }

  public String getAlternativeJrePath() {
    return ALTERNATIVE_JRE_PATH;
  }

  public void setAlternativeJrePath(String ALTERNATIVE_JRE_PATH) {
    this.ALTERNATIVE_JRE_PATH = ALTERNATIVE_JRE_PATH;
  }

  public boolean isAlternativeJreEnabled() {
    return ALTERNATIVE_JRE_PATH_ENABLED;
  }

  public void setAlternativeJreEnabled(boolean ALTERNATIVE_JRE_PATH_ENABLED) {
    this.ALTERNATIVE_JRE_PATH_ENABLED = ALTERNATIVE_JRE_PATH_ENABLED;
  }

  private static void fillParameterList(ParametersList list, @Nullable String value) {
    if (value == null) return;

    for (String parameter : value.split(" ")) {
      if (parameter != null && parameter.length() > 0) {
        list.add(parameter);
      }
    }
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    if (getModule() == null) {
      throw new RuntimeConfigurationException(DevKitBundle.message("run.configuration.no.module.specified"));
    }
    String moduleName = ReadAction.compute(() -> getModule().getName());
    if (ModuleManager.getInstance(getProject()).findModuleByName(moduleName) == null) {
      throw new RuntimeConfigurationException(DevKitBundle.message("run.configuration.no.module.specified"));
    }
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(getModule());
    final Sdk sdk = rootManager.getSdk();
    if (sdk == null) {
      throw new RuntimeConfigurationException(DevKitBundle.message("sdk.no.specified", moduleName));
    }
    if (IdeaJdk.findIdeaJdk(sdk) == null) {
      throw new RuntimeConfigurationException(DevKitBundle.message("sdk.type.incorrect", moduleName));
    }
  }


  @Override
  public Module @NotNull [] getModules() {
    final Module module = getModule();
    return module != null ? new Module[]{module} : Module.EMPTY_ARRAY;
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    Element module = element.getChild(MODULE);
    if (module != null) {
      myModuleName = module.getAttributeValue(NAME);
    }
    DefaultJDOMExternalizer.readExternal(this, element);
    final Element altElement = element.getChild(ALTERNATIVE_PATH_ELEMENT);
    if (altElement != null) {
      ALTERNATIVE_JRE_PATH = altElement.getAttributeValue(PATH);
      final String enabledAttr = altElement.getAttributeValue(ALTERNATIVE_PATH_ENABLED_ATTR);
      ALTERNATIVE_JRE_PATH_ENABLED = enabledAttr != null && Boolean.parseBoolean(enabledAttr);
    }
    super.readExternal(element);
    if (getPredefinedLogFiles().isEmpty() && getLogFiles().isEmpty()) {
      addPredefinedLogFile(new PredefinedLogFile(LOG_FILE_NAME, true));
    }
  }

  @Override
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    Element moduleElement = new Element(MODULE);
    moduleElement.setAttribute(NAME, ApplicationManager.getApplication().runReadAction(new Computable<>() {
      @Override
      public String compute() {
        final Module module = getModule();
        return module != null ? module.getName()
                              : myModuleName != null ? myModuleName : "";
      }
    }));
    element.addContent(moduleElement);
    DefaultJDOMExternalizer.writeExternal(this, element);
    if (!StringUtil.isEmptyOrSpaces(ALTERNATIVE_JRE_PATH)) {
      Element altElement = new Element(ALTERNATIVE_PATH_ELEMENT);
      altElement.setAttribute(PATH, ALTERNATIVE_JRE_PATH);
      altElement.setAttribute(ALTERNATIVE_PATH_ENABLED_ATTR, String.valueOf(ALTERNATIVE_JRE_PATH_ENABLED));
      element.addContent(altElement);
    }
    super.writeExternal(element);
  }

  public @Nullable Module getModule() {
    if (myModule == null && myModuleName != null && !getProject().isDisposed()) {
      myModule = ModuleManager.getInstance(getProject()).findModuleByName(myModuleName);
    }
    if (myModule != null && myModule.isDisposed()) {
      myModule = null;
    }
    return myModule;
  }

  public void setModule(Module module) {
    myModule = module;
  }

  private static @Nullable String getPluginId(Module plugin) {
    final XmlFile pluginXml = PluginModuleType.getPluginXml(plugin);
    if (pluginXml == null) return null;

    final IdeaPlugin ideaPlugin = DescriptorUtil.getIdeaPlugin(pluginXml);
    return ideaPlugin == null ? null : ideaPlugin.getPluginId();
  }
}
