package org.jetbrains.javafx.run;

import com.intellij.execution.CommonProgramRunConfigurationParameters;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.JavaFxBundle;
import org.jetbrains.javafx.JavaFxUtil;
import org.jetbrains.javafx.facet.JavaFxFacet;
import org.jetbrains.javafx.sdk.JavaFxSdkUtil;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxRunConfiguration extends ModuleBasedConfiguration<RunConfigurationModule>
  implements CommonProgramRunConfigurationParameters, RunProfile {

  // public for serialization
  public String MAIN_SCRIPT = "";
  public String PROGRAM_PARAMETERS = "";
  public boolean PASS_PARENT_VARIABLES = true;
  public String JAVA_FX_PARAMETERS = "";

  private String myWorkingDirectory;
  private Map<String, String> myEnvironmentVariables = new HashMap<String, String>();

  public JavaFxRunConfiguration(final Project project, final ConfigurationFactory factory, final String name) {
    super(name, new RunConfigurationModule(project), factory);
    myWorkingDirectory = project.getLocation();
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new JavaFxRunConfigurationEditor(getProject());
  }

  @Override
  public Collection<Module> getValidModules() {
    final Collection<Module> modules = getAllModules();
    final Set<Module> res = new HashSet<Module>();
    for (Module module : modules) {
      if (JavaFxSdkUtil.isHasValidSdk(module)) {
        res.add(module);
      }
    }
    return res;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    PathMacroManager.getInstance(getProject()).expandPaths(element);
    super.readExternal(element);
    //RunConfigurationExtension.readSettings(this, element);
    DefaultJDOMExternalizer.readExternal(this, element);
    readModule(element);
    EnvironmentVariablesComponent.readExternal(element, getEnvs());
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    //RunConfigurationExtension.writeSettings(this, element);
    DefaultJDOMExternalizer.writeExternal(this, element);
    writeModule(element);
    EnvironmentVariablesComponent.writeExternal(element, getEnvs());
    PathMacroManager.getInstance(getProject()).collapsePathsRecursively(element);
  }

  @Override
  protected ModuleBasedConfiguration createInstance() {
    return new JavaFxRunConfiguration(getProject(), getFactory(), getName());
  }

  public JDOMExternalizable createRunnerSettings(ConfigurationInfoProvider provider) {
    return null;
  }


  public SettingsEditor<JDOMExternalizable> getRunnerSettingsEditor(ProgramRunner runner) {
    return null;
  }

  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment env) throws ExecutionException {
    final CommandLineState state = new CommandLineState(env) {
      @Override
      protected ProcessHandler startProcess() throws ExecutionException {
        final GeneralCommandLine commandLine = createCommandLine();
        final OSProcessHandler processHandler = new OSProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString());
        ProcessTerminatedListener.attach(processHandler);
        return processHandler;
      }

      private GeneralCommandLine createCommandLine() throws ExecutionException {
        final Module module = getModule();
        if (module == null) {
          throw new ExecutionException(JavaFxBundle.message("invalid.module"));
        }
        final JavaFxFacet facet = FacetManager.getInstance(module).getFacetByType(JavaFxFacet.ID);
        if (facet == null) {
          throw new ExecutionException(JavaFxBundle.message("invalid.facet"));
        }
        final Sdk sdk = JavaFxSdkUtil.getSdk(facet);
        if (sdk == null) {
          throw new ExecutionException(JavaFxBundle.message("couldnt.find.javafx.sdk"));
        }
        final VirtualFile mainScriptFile = LocalFileSystem.getInstance().findFileByPath(MAIN_SCRIPT);
        if (mainScriptFile == null) {
          throw new ExecutionException(JavaFxBundle.message("couldnt.find.main.class"));
        }
        final String compilerOutputPath =
          JavaFxUtil.getCompilerOutputPath(ModuleUtil.findModuleForFile(mainScriptFile, module.getProject()));
        if (compilerOutputPath == null) {
          throw new ExecutionException(JavaFxBundle.message("wrong.compiler.output.path"));
        }

        final String binPath = FileUtil.toSystemDependentName(sdk.getHomePath() + "/bin/javafx");
        final GeneralCommandLine commandLine = new GeneralCommandLine();
        commandLine.setExePath(binPath);
        if (!StringUtil.isEmptyOrSpaces(JAVA_FX_PARAMETERS)) {
          commandLine.addParameters(JAVA_FX_PARAMETERS.split(" "));
        }
        commandLine.addParameter("-cp");
        commandLine.addParameter(FileUtil.toSystemDependentName(compilerOutputPath));
        commandLine.addParameter(JavaFxUtil.scriptNameToClassName(module.getProject(), MAIN_SCRIPT));
        if (!StringUtil.isEmptyOrSpaces(PROGRAM_PARAMETERS)) {
          commandLine.addParameters(PROGRAM_PARAMETERS.split(" "));
        }
        commandLine.setWorkDirectory(myWorkingDirectory);
        commandLine.setEnvParams(myEnvironmentVariables);
        commandLine.setPassParentEnvs(PASS_PARENT_VARIABLES);
        return commandLine;
      }
    };
    state.setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(getProject()));
    return state;
  }

  @Nullable
  private Module getModule() {
    final Module[] modules = getModules();
    if (modules.length != 1) {
      return null;
    }
    return modules[0];
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
    getConfigurationModule().checkForWarning();
    final File file = new File(MAIN_SCRIPT);
    if ("".equals(MAIN_SCRIPT) || !file.exists()) {
      throw new RuntimeConfigurationException(JavaFxBundle.message("invalid.main.script"));
    }
    final Module module = getModule();
    if (module == null) {
      throw new RuntimeConfigurationException(JavaFxBundle.message("invalid.module"));
    }
    if (!JavaFxSdkUtil.isHasValidSdk(module)) {
      throw new RuntimeConfigurationException(JavaFxBundle.message("invalid.sdk"));
    }
    final VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
    final PsiFile psiFile = PsiManager.getInstance(module.getProject()).findFile(virtualFile);
    if (psiFile == null || !RunnableScriptUtil.isRunnable(psiFile)) {
      throw new RuntimeConfigurationException(JavaFxBundle.message("invalid.main.script"));
    }
  }

  @Override
  public String suggestedName() {
    if (MAIN_SCRIPT != null && !"".equals(MAIN_SCRIPT)) {
      return StringUtil.getPackageName(StringUtil.getShortName(MAIN_SCRIPT, '/'));
    }
    return "";
  }

  public String getMainScript() {
    return FileUtil.toSystemDependentName(MAIN_SCRIPT);
  }

  public void setMainScript(String mainScript) {
    MAIN_SCRIPT = FileUtil.toSystemIndependentName(mainScript);
  }

  public void setProgramParameters(String value) {
    PROGRAM_PARAMETERS = value;
  }

  public String getProgramParameters() {
    return PROGRAM_PARAMETERS;
  }

  public String getWorkingDirectory() {
    return FileUtil.toSystemDependentName(myWorkingDirectory);
  }

  public void setEnvs(@NotNull Map<String, String> envs) {
    myEnvironmentVariables = envs;
  }

  @NotNull
  public Map<String, String> getEnvs() {
    return myEnvironmentVariables;
  }

  public void setPassParentEnvs(boolean passParentEnvs) {
    PASS_PARENT_VARIABLES = passParentEnvs;
  }

  public boolean isPassParentEnvs() {
    return PASS_PARENT_VARIABLES;
  }

  public void setWorkingDirectory(String workingDirectory) {
    myWorkingDirectory = FileUtil.toSystemIndependentName(workingDirectory);
  }

  public String getJavaFxParameters() {
    return JAVA_FX_PARAMETERS;
  }

  public void setJavaFxParameters(String javaFxParameters) {
    JAVA_FX_PARAMETERS = javaFxParameters;
  }
}
