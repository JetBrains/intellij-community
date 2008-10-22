package org.jetbrains.plugins.gant.runner;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.facet.FacetManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gant.GantBundle;
import org.jetbrains.plugins.gant.GantIcons;
import org.jetbrains.plugins.gant.config.GantConfigUtils;
import org.jetbrains.plugins.gant.config.grails.GantGrailsFacet;
import org.jetbrains.plugins.gant.config.groovy.GantGroovyFacet;
import org.jetbrains.plugins.grails.config.GrailsConfigUtils;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.GroovyIcons;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.runner.RunnerUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author ilyas
 */
public class GantScriptRunConfiguration extends ModuleBasedConfiguration {
  private GantScriptConfigurationFactory factory;
  public String vmParams;
  public boolean isDebugEnabled;
  public String scriptParams;
  public String targets;
  public String scriptPath;
  public String workDir = ".";

  @NonNls public static final String GANT_STARTER = "org.codehaus.groovy.tools.GroovyStarter";
  @NonNls public static final String GANT_MAIN = "gant.Gant";
  @NonNls private static final String GANT_STARTER_CONF = "/conf/gant-starter.conf";

  // JVM parameters
  @NonNls private static final String DGROOVY_STARTER_CONF = "-Dgroovy.starter.conf=";
  @NonNls private static final String DTOOLS_JAR = "-Dtools.jar=";
  @NonNls private static final String DGANT_HOME = "-Dgant.home=";
  @NonNls private static final String DSCRIPT_NAME = "-Dscript.name=";
  @NonNls private static final String DPROGRAM_NAME = "-Dprogram.name=";
  @NonNls private static final String DGROOVY_HOME = "-Dgroovy.home=";

  public GantScriptRunConfiguration(GantScriptConfigurationFactory factory, Project project, String name) {
    super(name, new RunConfigurationModule(project), factory);
    this.factory = factory;
  }

  public Collection<Module> getValidModules() {
    Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    ArrayList<Module> res = new ArrayList<Module>();
    for (Module module : modules) {
      if (FacetManager.getInstance(module).getFacetsByType(GantGrailsFacet.ID).size() > 0 ||
          FacetManager.getInstance(module).getFacetsByType(GantGroovyFacet.ID).size() > 0) {
        res.add(module);
      }
    }
    return res;
  }

  public void setWorkDir(String dir) {
    workDir = dir;
  }

  public String getWorkDir() {
    return workDir;
  }

  public String getAbsoluteWorkDir() {
    if (!new File(workDir).isAbsolute()) {
      return new File(getProject().getLocation(), workDir).getAbsolutePath();
    }
    return workDir;
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    readModule(element);
    scriptPath = JDOMExternalizer.readString(element, "path");
    vmParams = JDOMExternalizer.readString(element, "vmparams");
    scriptParams = JDOMExternalizer.readString(element, "params");
    targets = JDOMExternalizer.readString(element, "targets");
    workDir = JDOMExternalizer.readString(element, "workDir");
    isDebugEnabled = Boolean.parseBoolean(JDOMExternalizer.readString(element, "debug"));
    workDir = getWorkDir();
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    writeModule(element);
    JDOMExternalizer.write(element, "path", scriptPath);
    JDOMExternalizer.write(element, "vmparams", vmParams);
    JDOMExternalizer.write(element, "params", scriptParams);
    JDOMExternalizer.write(element, "targets", targets);
    JDOMExternalizer.write(element, "workDir", workDir);
    JDOMExternalizer.write(element, "debug", isDebugEnabled);
  }

  protected ModuleBasedConfiguration createInstance() {
    return new GantScriptRunConfiguration(factory, getConfigurationModule().getProject(), getName());
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new GantRunConfigurationEditor();
  }

  private void configureJavaParams(JavaParameters params, Module module) throws CantRunException {

    // Setting up classpath
    RunnerUtil.configureScriptSystemClassPath(params, module);

    params.setWorkingDirectory(getAbsoluteWorkDir());

    //add starter configuration parameters
    String gantHome = GantConfigUtils.getInstance().getSDKInstallPath(module);
    final String confpath = gantHome + GANT_STARTER_CONF;

    // -Dant.home
    params.getVMParametersList().addParametersString("-Dant.home=");
    // -Dgant.home
    params.getVMParametersList().addParametersString(DGANT_HOME + "\"" + gantHome + "\"");
    // -Dscript/name
    params.getVMParametersList()
      .addParametersString(DSCRIPT_NAME + "\"" + gantHome + File.separator + "bin" + File.separator + "gant" + "\"");
    // -Dprogram.name
    params.getVMParametersList().addParametersString(DPROGRAM_NAME + "gant");
    // -Dgroovy.starter.conf
    params.getVMParametersList().add(DGROOVY_STARTER_CONF + confpath);
    // -Dgroovy.home
    final String path = GroovyConfigUtils.getInstance().getSDKInstallPath(module);
    params.getVMParametersList()
      .add(DGROOVY_HOME + (path.length() == 0 ? GrailsConfigUtils.getInstance().getSDKInstallPath(module) : path));
    // -Dtools.jar
    Sdk jdk = params.getJdk();
    if (jdk != null && jdk.getSdkType() instanceof JavaSdkType) {
      String toolsPath = ((JavaSdkType)jdk.getSdkType()).getToolsPath(jdk);
      if (toolsPath != null) {
        params.getVMParametersList().add(DTOOLS_JAR + toolsPath);
      }
    }

    // add user parameters
    params.getVMParametersList().addParametersString(vmParams);

    // set starter class
    params.setMainClass(GANT_STARTER);
  }

  private void configureGantStarter(JavaParameters params, final Module module) {
    // add GantStarter parameters

    String gantHome = GantConfigUtils.getInstance().getSDKInstallPath(module);
    final String confpath = gantHome + GANT_STARTER_CONF;
    params.getVMParametersList().add(DGROOVY_STARTER_CONF + confpath);

    params.getProgramParametersList().add("--main");
    params.getProgramParametersList().add(GANT_MAIN);
    params.getProgramParametersList().add("--conf");
    params.getProgramParametersList().add(confpath);
    params.getProgramParametersList().add("--classpath");

    // Clear module libraries from JDK's occurrences
    StringBuffer buffer = RunnerUtil.getClearClassPathString(params, module);

    params.getProgramParametersList().add("\"" + workDir + File.pathSeparator + buffer.toString() + "\"");
    if (isDebugEnabled) {
      params.getProgramParametersList().add("--debug");
    }
  }

  private void configureScript(JavaParameters params) {
    // add scriptGroovy
    params.getProgramParametersList().add("--file");
    params.getProgramParametersList().add(scriptPath);

    // add Gant script parameters
    params.getProgramParametersList().addParametersString(scriptParams);

    if (targets != null) {
      Iterable<String> targetList = StringUtil.tokenize(targets, "");
      for (String target : targetList) {
        params.getProgramParametersList().addParametersString(" " + target);
      }
    }
  }

  public Module getModule() {
    return getConfigurationModule().getModule();
  }

  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException {
    final Module module = getModule();
    if (module == null) {
      throw new ExecutionException("Module is not specified");
    }

    if (!GroovyConfigUtils.getInstance().isSDKConfiguredToRun(module) && !GrailsConfigUtils.getInstance().isSDKConfiguredToRun(module)) {
      //throw new ExecutionException("Gant is not configured");
      Messages.showErrorDialog(module.getProject(),
                               ExecutionBundle.message("error.running.configuration.with.error.error.message", getName(),
                                                       "Groovy is not configured"), ExecutionBundle.message("run.error.message.title"));

      int result = Messages.showOkCancelDialog(GroovyBundle.message("groovy.configure.facet.question.text", module.getName()),
                                               GroovyBundle.message("groovy.configure.facet.question"), GroovyIcons.GROOVY_ICON_32x32);
      if (result == 0) {
        ModulesConfigurator.showDialog(module.getProject(), module.getName(), ClasspathEditor.NAME, false);
      }
      return null;
    }

    if (!GantConfigUtils.getInstance().isSDKConfiguredToRun(module)) {
      //throw new ExecutionException("Gant is not configured");
      Messages.showErrorDialog(module.getProject(),
                               ExecutionBundle.message("error.running.configuration.with.error.error.message", getName(),
                                                       "Gant is not configured"), ExecutionBundle.message("run.error.message.title"));

      int result = Messages
        .showOkCancelDialog(GantBundle.message("gant.configure.facet.question.text"), GantBundle.message("gant.configure.facet.question"),
                            GantIcons.GANT_ICON_16x16);
      if (result == 0) {
        ModulesConfigurator.showDialog(module.getProject(), module.getName(), ClasspathEditor.NAME, false);
      }
      return null;
    }

    final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    final Sdk sdk = rootManager.getSdk();
    if (sdk == null || !(sdk.getSdkType() instanceof JavaSdkType)) {
      throw CantRunException.noJdkForModule(getModule());
    }

    final JavaCommandLineState state = new JavaCommandLineState(environment) {
      protected JavaParameters createJavaParameters() throws ExecutionException {
        JavaParameters params = new JavaParameters();

        configureJavaParams(params, module);
        configureGantStarter(params, module);
        configureScript(params);

        return params;
      }
    };

    state.setConsoleBuilder(TextConsoleBuilderFactory.getInstance().createBuilder(getProject()));
    return state;

  }
}