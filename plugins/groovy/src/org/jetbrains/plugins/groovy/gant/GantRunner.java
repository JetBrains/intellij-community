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
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunner;
import org.jetbrains.plugins.groovy.util.GroovyUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

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
  public boolean ensureRunnerConfigured(Module module, final String confName) {
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
  public void configureCommandLine(JavaParameters params, Module module, boolean tests, VirtualFile script, GroovyScriptRunConfiguration configuration) throws CantRunException {
    final VirtualFile groovyJar = findGroovyJar(module);
    if (groovyJar != null) {
      params.getClassPath().add(groovyJar);
    }

    setToolsJar(params);

    final String groovyHome = FileUtil.toSystemDependentName(ObjectUtils.assertNotNull(LibrariesUtil.getGroovyHomePath(module)));
    setGroovyHome(params, groovyHome);
    
    final String confPath = getConfPath(module);
    params.getVMParametersList().add("-Dgroovy.starter.conf=" + confPath);

    params.getVMParametersList().addParametersString(configuration.vmParams);
    params.setMainClass("org.codehaus.groovy.tools.GroovyStarter");

    params.getProgramParametersList().add("--conf");
    params.getProgramParametersList().add(confPath);

    params.getProgramParametersList().add("--classpath");
    params.getProgramParametersList().add(getClearClasspath(module, tests));

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

    params.getProgramParametersList().add("--main");
    params.getProgramParametersList().add("gant.Gant");

    params.getProgramParametersList().add("--file");
    params.getProgramParametersList().add(FileUtil.toSystemDependentName(configuration.scriptPath));

    params.getProgramParametersList().addParametersString(configuration.scriptParams);
  }

}
