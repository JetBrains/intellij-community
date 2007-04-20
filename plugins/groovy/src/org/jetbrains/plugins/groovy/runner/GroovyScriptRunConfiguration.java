package org.jetbrains.plugins.groovy.runner;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import org.jdom.Element;
import org.jetbrains.plugins.groovy.config.GroovyFacet;
import org.jetbrains.plugins.groovy.config.GroovyGrailsConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

class GroovyScriptRunConfiguration extends ModuleBasedConfiguration
{
  private GroovyScriptConfigurationFactory factory;
  public String vmParams;
  public String scriptParams;
  public String scriptPath;
  public String workDir = ".";

  public GroovyScriptRunConfiguration(GroovyScriptConfigurationFactory factory, Project project, String name)
  {
    super(name, new RunConfigurationModule(project, true), factory);
    this.factory = factory;
  }

  public Collection<Module> getValidModules()
  {
    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    ArrayList<Module> res = new ArrayList<Module>();
    for (Module module : modules)
      if (FacetManager.getInstance(module).getFacetsByType(GroovyFacet.ID).size() > 0)
        res.add(module);
    return res;
  }

  public void setAbsoluteWorkDir(String dir)
  {
    workDir = FileUtil.getRelativePath(new File(getProject().getBaseDir().getPath()), new File(dir));
  }

  public String getAbsoluteWorkDir()
  {
    String path = getProject().getBaseDir().getPath();
    try
    {
      return new File(path, workDir).getCanonicalFile().getAbsolutePath();
    } catch (IOException e)
    {
      return path;
    }
  }

  public void readExternal(Element element) throws InvalidDataException
  {
    super.readExternal(element);
    readModule(element);
    scriptPath = JDOMExternalizer.readString(element, "path");
    vmParams = JDOMExternalizer.readString(element, "vmparams");
    scriptParams = JDOMExternalizer.readString(element, "params");
    workDir = JDOMExternalizer.readString(element, "workDir");
  }

  public void writeExternal(Element element) throws WriteExternalException
  {
    super.writeExternal(element);
    writeModule(element);
    JDOMExternalizer.write(element, "path", scriptPath);
    JDOMExternalizer.write(element, "vmparams", vmParams);
    JDOMExternalizer.write(element, "params", scriptParams);
    JDOMExternalizer.write(element, "workDir", workDir);
  }

  protected ModuleBasedConfiguration createInstance()
  {
    return new GroovyScriptRunConfiguration(factory, getConfigurationModule().getProject(), getName());
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor()
  {
    return new GroovyRunConfigurationEditor(getProject());
  }

  public RunProfileState getState(
          DataContext context,
          RunnerInfo runnerInfo,
          RunnerSettings runnerSettings,
          ConfigurationPerRunnerSettings configurationSettings) throws ExecutionException
  {
    GroovyGrailsConfiguration groovyConfig = GroovyGrailsConfiguration.getInstance();
    if (!groovyConfig.isGroovyConfigured(null))
    {
      throw new ExecutionException("Groovy is not configured");
    }

    final Module module = getModule();
    if (module == null)
    {
      throw new ExecutionException("Module is not specified");
    }

    final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    final ProjectJdk jdk = rootManager.getJdk();
    if (jdk == null)
    {
      throw CantRunException.noJdkForModule(getModule());
    }

    final JavaCommandLineState state = new JavaCommandLineState(runnerSettings, configurationSettings)
    {
      protected JavaParameters createJavaParameters() throws ExecutionException
      {
        JavaParameters params = new JavaParameters();

        params.configureByModule(module, JavaParameters.JDK_AND_CLASSES);

        params.setWorkingDirectory(getAbsoluteWorkDir());
        params.getVMParametersList().addParametersString(vmParams);
        params.getProgramParametersList().add(scriptPath);
        params.getProgramParametersList().addParametersString(scriptParams);
        params.setMainClass("groovy.lang.GroovyShell");

        return params;
      }
    };

    state.setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(getProject()));
    state.setModulesToCompile(getModules());
    return state;
  }

  public Module getModule()
  {
    return getConfigurationModule().getModule();
  }
}
