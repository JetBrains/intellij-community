package org.jetbrains.plugins.gant.runner;

import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gant.GantBundle;
import org.jetbrains.plugins.gant.GantIcons;
import org.jetbrains.plugins.gant.config.GantConfigUtils;
import org.jetbrains.plugins.groovy.runner.AbstractGroovyScriptRunConfiguration;
import org.jetbrains.plugins.groovy.util.GroovyUtils;

import java.io.File;

/**
 * @author ilyas
 */
public class GantScriptRunConfiguration extends AbstractGroovyScriptRunConfiguration {

  public GantScriptRunConfiguration(ConfigurationFactory factory, Project project, String name) {
    super(name, project, factory);
  }

  @Override
  protected boolean isValidModule(Module module) {
    return GantConfigUtils.getInstance().isSDKConfiguredToRun(module);
  }

  protected ModuleBasedConfiguration createInstance() {
    return new GantScriptRunConfiguration(getFactory(), getConfigurationModule().getProject(), getName());
  }

  @Override
  protected boolean ensureRunnerConfigured(Module module, final String groovyHomePath) {
    if (!super.ensureRunnerConfigured(module, groovyHomePath)) {
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
  protected String getConfPath(@NotNull Module module) {
    String gantHome = GantConfigUtils.getInstance().getSDKInstallPath(module);
    String confPath = FileUtil.toSystemDependentName(gantHome + "/conf/gant-starter.conf");
    if (new File(confPath).exists()) {
      return confPath;
    }

    return super.getConfPath(module);
  }

  @Override
  protected void configureCommandLine(JavaParameters params, Module module, boolean tests, VirtualFile script, String confPath,
                                      final String groovyHome) throws CantRunException {
    defaultGroovyStarter(params, module, confPath, tests, groovyHome);

    if (groovyHome.contains("grails")) {
      params.getClassPath().addAllFiles(GroovyUtils.getFilesInDirectoryByPattern(groovyHome + "/lib", ".*\\.jar"));
    }

    String gantHome = GantConfigUtils.getInstance().getSDKInstallPath(module);

    String antHome = System.getenv("ANT_HOME");
    if (StringUtil.isEmpty(antHome)) {
      antHome = groovyHome;
    }

    params.getVMParametersList().addParametersString("-Dant.home=" + antHome);
    params.getVMParametersList().addParametersString("-Dgant.home=\"" + gantHome + "\"");
    //params.getVMParametersList().addParametersString("-Dscript.name=\"" + gantHome + File.separator + "bin" + File.separator + "gant\"");
    //params.getVMParametersList().addParametersString("-Dprogram.name=gant");

    params.getProgramParametersList().add("--main");
    params.getProgramParametersList().add("gant.Gant");

    params.getProgramParametersList().add("--file");
    params.getProgramParametersList().add(FileUtil.toSystemDependentName(scriptPath));

    params.getProgramParametersList().addParametersString(scriptParams);
  }

}
