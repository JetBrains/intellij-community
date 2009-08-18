package org.jetbrains.plugins.groovy.runner;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.util.PathUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Pattern;

/**
 * @author peter
 */
public abstract class AbstractGroovyScriptRunConfiguration extends ModuleBasedConfiguration<RunConfigurationModule> {
  public String vmParams;
  public String workDir;
  public boolean isDebugEnabled;
  public String scriptParams;
  public String scriptPath;

  public AbstractGroovyScriptRunConfiguration(final String name, final Project project, final ConfigurationFactory factory) {
    super(name, new RunConfigurationModule(project), factory);
    workDir = PathUtil.getLocalPath(project.getBaseDir());
  }

  public void setWorkDir(String dir) {
    workDir = dir;
  }

  public String getWorkDir() {
    return workDir;
  }

  @Nullable
  public Module getModule() {
    return getConfigurationModule().getModule();
  }

  private String getAbsoluteWorkDir() {
    if (!new File(workDir).isAbsolute()) {
      return new File(getProject().getLocation(), workDir).getAbsolutePath();
    }
    return workDir;
  }

  public Collection<Module> getValidModules() {
    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    ArrayList<Module> res = new ArrayList<Module>();
    for (Module module : modules) {
      if (isValidModule(module)) {
        res.add(module);
      }
    }
    return res;
  }

  protected abstract boolean isValidModule(Module module);

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    readModule(element);
    scriptPath = JDOMExternalizer.readString(element, "path");
    vmParams = JDOMExternalizer.readString(element, "vmparams");
    scriptParams = JDOMExternalizer.readString(element, "params");
    final String wrk = JDOMExternalizer.readString(element, "workDir");
    if (!".".equals(wrk)) {
      workDir = wrk;
    }
    isDebugEnabled = Boolean.parseBoolean(JDOMExternalizer.readString(element, "debug"));
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    writeModule(element);
    JDOMExternalizer.write(element, "path", scriptPath);
    JDOMExternalizer.write(element, "vmparams", vmParams);
    JDOMExternalizer.write(element, "params", scriptParams);
    JDOMExternalizer.write(element, "workDir", workDir);
    JDOMExternalizer.write(element, "debug", isDebugEnabled);
  }

  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException {
    final Module module = getModule();
    if (module == null) {
      throw new ExecutionException("Module is not specified");
    }

    final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    final Sdk sdk = rootManager.getSdk();
    if (sdk == null || !(sdk.getSdkType() instanceof JavaSdkType)) {
      throw CantRunException.noJdkForModule(module);
    }

    final String groovyHomePath = LibrariesUtil.getGroovyHomePath(module);
    if (groovyHomePath == null) {
      Messages.showErrorDialog(module.getProject(),
                               ExecutionBundle.message("error.running.configuration.with.error.error.message", getName(),
                                                       "Groovy is not configured"), ExecutionBundle.message("run.error.message.title"));

      ModulesConfigurator.showDialog(module.getProject(), module.getName(), ClasspathEditor.NAME, false);
      return null;
    }


    if (!ensureRunnerConfigured(module, groovyHomePath)) {
      return null;
    }

    final VirtualFile script = LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(scriptPath));
    if (script == null) {
      throw new CantRunException("Cannot find script " + scriptPath);
    }

    final boolean isTests = ProjectRootManager.getInstance(getProject()).getFileIndex().isInTestSourceContent(script);

    final JavaCommandLineState state = new JavaCommandLineState(environment) {
      protected JavaParameters createJavaParameters() throws ExecutionException {
        JavaParameters params = new JavaParameters();
        params.setCharset(null);

        params.setJdk(ModuleRootManager.getInstance(module).getSdk());
        params.setWorkingDirectory(getAbsoluteWorkDir());

        configureCommandLine(params, module, isTests, script, getConfPath(module), FileUtil.toSystemDependentName(groovyHomePath));

        if (isDebugEnabled) {
          params.getProgramParametersList().add("--debug");
        }

        return params;
      }
    };

    state.setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(getProject()));
    return state;

  }

  protected String getConfPath(@NotNull Module module) {
    String confpath = FileUtil.toSystemDependentName(LibrariesUtil.getGroovyHomePath(module) + "/conf/groovy-starter.conf");
    if (new File(confpath).exists()) {
      return confpath;
    }

    try {
      final String jarPath = PathUtil.getJarPathForClass(GroovyScriptRunConfiguration.class);
      if (new File(jarPath).isFile()) { //jar; distribution mode
        return new File(jarPath, "../groovy-starter.conf").getCanonicalPath();
      }

      //else, it's directory in out, development mode
      return new File(jarPath, "conf/groovy-starter.conf").getCanonicalPath();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }


  protected abstract void configureCommandLine(JavaParameters params, Module module, boolean tests, VirtualFile script, String confPath,
                                               final String groovyHome) throws CantRunException;

  protected boolean ensureRunnerConfigured(Module module, final String groovyHomePath) {
    return true;
  }

  protected void defaultGroovyStarter(JavaParameters params, Module module, String confPath, boolean tests, final String groovyHome) throws CantRunException {
    addGroovyJar(params, module);

    params.getVMParametersList().addParametersString("-Dgroovy.home=" + "\"" + groovyHome + "\"");
    if (groovyHome.contains("grails")) { //a bit of a hack
      params.getVMParametersList().addParametersString("-Dgrails.home=" + "\"" + groovyHome + "\"");
    }
    if (groovyHome.contains("griffon")) { //a bit of a hack
      params.getVMParametersList().addParametersString("-Dgriffon.home=" + "\"" + groovyHome + "\"");
    }
    params.getVMParametersList().add("-Dgroovy.starter.conf=" + confPath);

    setToolsJar(params);

    params.getVMParametersList().addParametersString(vmParams);
    params.setMainClass("org.codehaus.groovy.tools.GroovyStarter");

    params.getProgramParametersList().add("--conf");
    params.getProgramParametersList().add(confPath);

    params.getProgramParametersList().add("--classpath");
    params.getProgramParametersList().add(getClearClasspath(module, tests));
  }

  private static void setToolsJar(JavaParameters params) {
    Sdk jdk = params.getJdk();
    if (jdk != null && jdk.getSdkType() instanceof JavaSdkType) {
      String toolsPath = ((JavaSdkType)jdk.getSdkType()).getToolsPath(jdk);
      if (toolsPath != null) {
        params.getVMParametersList().add("-Dtools.jar=" + toolsPath);
      }
    }
  }

  private static void addGroovyJar(JavaParameters params, Module module) {
    final Pattern pattern = Pattern.compile(".*[\\\\/]groovy[^\\\\/]*jar");
    for (Library library : GroovyConfigUtils.getInstance().getSDKLibrariesByModule(module)) {
      for (VirtualFile root : library.getFiles(OrderRootType.CLASSES)) {
        if (pattern.matcher(root.getPresentableUrl()).matches()) {
          params.getClassPath().add(root);
          return;
        }
      }
    }
  }

  protected static String getClearClasspath(Module module, boolean isTests) throws CantRunException {
    final JavaParameters tmp = new JavaParameters();
    tmp.configureByModule(module, isTests ? JavaParameters.JDK_AND_CLASSES_AND_TESTS : JavaParameters.JDK_AND_CLASSES);
    StringBuffer buffer = RunnerUtil.getClearClassPathString(tmp, module);

    return buffer.toString();
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new GroovyRunConfigurationEditor();
  }
}
