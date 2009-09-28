package org.jetbrains.plugins.groovy.runner;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.SimpleJavaSdkType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.PathUtil;
import com.intellij.util.SystemProperties;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptType;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

/**
 * @author peter
 */
public class GroovyScriptRunConfiguration extends ModuleBasedConfiguration<RunConfigurationModule> {
  public String vmParams;
  public String workDir;
  public boolean isDebugEnabled;
  public String scriptParams;
  public String scriptPath;

  public GroovyScriptRunConfiguration(final String name, final Project project, final ConfigurationFactory factory) {
    super(name, new RunConfigurationModule(project), factory);
    workDir = PathUtil.getLocalPath(project.getBaseDir());
  }

  @Override
  protected ModuleBasedConfiguration createInstance() {
    return new GroovyScriptRunConfiguration(getName(), getProject(), getFactory());
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
    final GroovyScriptRunner scriptRunner = findConfiguration();
    if (scriptRunner == null) {
      return Arrays.asList(modules);
    }


    ArrayList<Module> res = new ArrayList<Module>();
    for (Module module : modules) {
      if (scriptRunner.isValidModule(module)) {
        res.add(module);
      }
    }
    return res;
  }

  @Nullable
  private GroovyScriptRunner findConfiguration() {
    final VirtualFile scriptFile = getScriptFile();
    if (scriptFile == null) {
      return null;
    }

    final PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(scriptFile);
    if (!(psiFile instanceof GroovyFile) || !((GroovyFile)psiFile).isScript()) {
      return null;
    }

    return GroovyScriptType.getScriptType((GroovyFile)psiFile).getRunner();
  }

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
    final VirtualFile script = getScriptFile();
    if (script == null) {
      throw new CantRunException("Cannot find script " + scriptPath);
    }

    final GroovyScriptRunner scriptRunner = findConfiguration();
    if (scriptRunner == null) {
      throw new CantRunException("Unknown script type " + scriptPath);
    }

    final Module module = getModule();
    if (!scriptRunner.ensureRunnerConfigured(module, getName(), getProject())) {
      return null;
    }

    final boolean tests = ProjectRootManager.getInstance(getProject()).getFileIndex().isInTestSourceContent(script);

    final JavaCommandLineState state = new JavaCommandLineState(environment) {
      protected JavaParameters createJavaParameters() throws ExecutionException {
        JavaParameters params = new JavaParameters();
        params.setCharset(null);

        if (module != null) {
          final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
          if (sdk != null && sdk.getSdkType() instanceof JavaSdkType) {
            params.setJdk(sdk);
          }
        }
        if (params.getJdk() == null) {
          params.setJdk(new SimpleJavaSdkType().createJdk("tmp", SystemProperties.getJavaHome()));
        }
        params.setWorkingDirectory(getAbsoluteWorkDir());

        scriptRunner.configureCommandLine(params, module, tests, script, GroovyScriptRunConfiguration.this);

        return params;
      }
    };

    state.setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(getProject()));
    return state;

  }

  @Nullable
  private VirtualFile getScriptFile() {
    return LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(scriptPath));
  }


  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new GroovyRunConfigurationEditor();
  }
}
