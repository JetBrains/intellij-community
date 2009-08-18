package org.jetbrains.plugins.gant.runner;

import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.module.Module;
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
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.gant.GantBundle;
import org.jetbrains.plugins.gant.GantIcons;
import org.jetbrains.plugins.gant.config.GantConfigUtils;
import org.jetbrains.plugins.groovy.runner.AbstractGroovyScriptRunConfiguration;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration;
import org.jetbrains.plugins.groovy.runner.RunnerUtil;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.io.File;

/**
 * @author ilyas
 */
public class GantScriptRunConfiguration extends AbstractGroovyScriptRunConfiguration {
  public String targets;
  public String antHome = "";

  @NonNls public static final String GANT_STARTER = "org.codehaus.groovy.tools.GroovyStarter";
  @NonNls public static final String GANT_MAIN = "gant.Gant";
  @NonNls private static final String GANT_STARTER_CONF = "/conf/gant-starter.conf";

  public GantScriptRunConfiguration(ConfigurationFactory factory, Project project, String name) {
    super(name, project, factory);
  }

  @Override
  protected boolean isValidModule(Module module) {
    return GantConfigUtils.getInstance().isSDKConfiguredToRun(module);
  }

  public void readExternal(Element element) throws InvalidDataException {
    super.readExternal(element);
    targets = JDOMExternalizer.readString(element, "targets");
    antHome  = JDOMExternalizer.readString(element, "antHome");
  }

  public void writeExternal(Element element) throws WriteExternalException {
    super.writeExternal(element);
    JDOMExternalizer.write(element, "targets", targets);
    JDOMExternalizer.write(element, "antHome", antHome);
  }

  protected ModuleBasedConfiguration createInstance() {
    return new GantScriptRunConfiguration(getFactory(), getConfigurationModule().getProject(), getName());
  }

  public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new GantRunConfigurationEditor();
  }

  private void configureJavaParams(JavaParameters params, Module module, String confpath) throws CantRunException {
    params.setJdk(ModuleRootManager.getInstance(module).getSdk());

    RunnerUtil.configureScriptSystemClassPath(params, module);

    params.setWorkingDirectory(getAbsoluteWorkDir());

    String gantHome = GantConfigUtils.getInstance().getSDKInstallPath(module);

    params.getVMParametersList().addParametersString("-Dant.home=" + antHome);
    params.getVMParametersList().addParametersString("-Dgant.home=" + "\"" + gantHome + "\"");
    params.getVMParametersList().addParametersString("-Dscript.name=" + "\"" + gantHome + File.separator + "bin" + File.separator + "gant" + "\"");
    params.getVMParametersList().addParametersString("-Dprogram.name=" + "gant");
    params.getVMParametersList().add(GroovyScriptRunConfiguration.DGROOVY_STARTER_CONF + confpath);

    params.getVMParametersList().add(GroovyScriptRunConfiguration.DGROOVY_HOME + LibrariesUtil.getGroovyHomePath(module));

    // -Dtools.jar
    Sdk jdk = params.getJdk();
    if (jdk != null && jdk.getSdkType() instanceof JavaSdkType) {
      String toolsPath = ((JavaSdkType)jdk.getSdkType()).getToolsPath(jdk);
      if (toolsPath != null) {
        params.getVMParametersList().add(GroovyScriptRunConfiguration.DTOOLS_JAR + toolsPath);
      }
    }

    // add user parameters
    params.getVMParametersList().addParametersString(vmParams);

    // set starter class
    params.setMainClass(GANT_STARTER);
  }

  private void configureGantStarter(JavaParameters params, final Module module, String confpath) throws CantRunException {
    params.getProgramParametersList().add("--main");
    params.getProgramParametersList().add(GANT_MAIN);
    params.getProgramParametersList().add("--conf");
    params.getProgramParametersList().add(confpath);
    params.getProgramParametersList().add("--classpath");

    // Clear module libraries from JDK's occurrences
    final JavaParameters tmp = new JavaParameters();
    tmp.configureByModule(module, JavaParameters.JDK_AND_CLASSES);
    StringBuffer buffer = RunnerUtil.getClearClassPathString(tmp, module);

    params.getProgramParametersList().add(buffer.toString());
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

  @Override
  protected boolean ensureRunnerConfigured(Module module) {
    if (!super.ensureRunnerConfigured(module)) {
      return false;
    }

    if (!isValidModule(module)) {
      int result = Messages
        .showOkCancelDialog(GantBundle.message("gant.configure.facet.question.text"), GantBundle.message("gant.configure.facet.question"),
                            GantIcons.GANT_ICON_16x16);
      if (result == 0) {
        ModulesConfigurator.showDialog(module.getProject(), module.getName(), ClasspathEditor.NAME, false);
      }
      if (!isValidModule(module)) {
        return false;
      }
    }

    return true;
  }

  @Override
  protected void configureCommandLine(JavaParameters params, Module module, boolean tests, VirtualFile script) throws CantRunException {
    String gantHome = GantConfigUtils.getInstance().getSDKInstallPath(module);
    String confpath = gantHome + GANT_STARTER_CONF;
    if (!new File(confpath).exists()) {
      confpath = GroovyScriptRunConfiguration.getConfPath(gantHome);
    }

    configureJavaParams(params, module, confpath);
    configureGantStarter(params, module, confpath);
    configureScript(params);
  }

}
