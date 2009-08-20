package org.jetbrains.plugins.groovy.gant;

import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.runner.AbstractGroovyScriptRunConfiguration;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunner;
import org.jetbrains.plugins.groovy.util.GroovyUtils;

import java.io.File;

/**
 * @author ilyas
 */
public class GantRunner extends GroovyScriptRunner {

  @Override
  public boolean isValidModule(Module module) {
    return GantUtils.isSDKConfiguredToRun(module);
  }

  @Override
  public boolean ensureRunnerConfigured(Module module, final String groovyHomePath) {
    if (!isValidModule(module)) {
      int result = Messages
        .showOkCancelDialog("Gant is not configured. Do you want to configure it?", "Configure Gant SDK",
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
  public String getConfPath(@NotNull Module module) {
    String gantHome = GantUtils.getSDKInstallPath(module);
    String confPath = FileUtil.toSystemDependentName(gantHome + "/conf/gant-starter.conf");
    if (new File(confPath).exists()) {
      return confPath;
    }

    return super.getConfPath(module);
  }

  @Override
  public void configureCommandLine(JavaParameters params, Module module, boolean tests, VirtualFile script, String confPath,
                                      final String groovyHome, AbstractGroovyScriptRunConfiguration configuration) throws CantRunException {
    defaultGroovyStarter(params, module, confPath, tests, groovyHome, configuration);

    if (groovyHome.contains("grails")) {
      params.getClassPath().addAllFiles(GroovyUtils.getFilesInDirectoryByPattern(groovyHome + "/lib", ".*\\.jar"));
    }

    String gantHome = GantUtils.getSDKInstallPath(module);

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
    params.getProgramParametersList().add(FileUtil.toSystemDependentName(configuration.scriptPath));

    params.getProgramParametersList().addParametersString(configuration.scriptParams);
  }

}
