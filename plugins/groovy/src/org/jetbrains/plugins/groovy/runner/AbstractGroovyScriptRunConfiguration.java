package org.jetbrains.plugins.groovy.runner;

import com.intellij.execution.configurations.*;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;
import org.jdom.Element;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author peter
 */
public abstract class AbstractGroovyScriptRunConfiguration extends ModuleBasedConfiguration<RunConfigurationModule> {
  public String vmParams;
  public String workDir;
  public boolean isDebugEnabled;
  public String scriptParams;
  public String scriptPath;
  @NonNls public static final String GROOVY_STARTER_CONF = "/conf/groovy-starter.conf";

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

  public String getAbsoluteWorkDir() {
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

  protected static String getConfPath(String groovyHome) {
    String confpath = FileUtil.toSystemDependentName(groovyHome + GROOVY_STARTER_CONF);
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

    if (!ensureRunnerConfigured(module)) {
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
        configureCommandLine(params, module, isTests, script);
        return params;
      }
    };

    state.setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(getProject()));
    return state;

  }

  protected abstract void configureCommandLine(JavaParameters params, Module module, boolean tests, VirtualFile script) throws CantRunException;

  protected boolean ensureRunnerConfigured(Module module) {
    if (LibrariesUtil.getGroovyHomePath(module) == null) {
      Messages.showErrorDialog(module.getProject(),
                               ExecutionBundle.message("error.running.configuration.with.error.error.message", getName(),
                                                       "Groovy is not configured"), ExecutionBundle.message("run.error.message.title"));

      ModulesConfigurator.showDialog(module.getProject(), module.getName(), ClasspathEditor.NAME, false);
      return false;
    }
    return true;
  }
}
