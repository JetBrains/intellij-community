package org.jetbrains.plugins.groovy.runner;

import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * @author peter
 */
public abstract class GroovyScriptRunner {

  public abstract boolean isValidModule(@NotNull Module module);

  public abstract boolean ensureRunnerConfigured(Module module, final String confName);

  public abstract void configureCommandLine(JavaParameters params, Module module, boolean tests, VirtualFile script,
                                            GroovyScriptRunConfiguration configuration) throws CantRunException;

  protected static String getConfPath(final String groovyHomePath) {
    String confpath = FileUtil.toSystemDependentName(groovyHomePath + "/conf/groovy-starter.conf");
    if (new File(confpath).exists()) {
      return confpath;
    }

    try {
      final String jarPath = PathUtil.getJarPathForClass(GroovyScriptRunner.class);
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

  protected static void setGroovyHome(JavaParameters params, String groovyHome) {
    params.getVMParametersList().addParametersString("-Dgroovy.home=" + "\"" + groovyHome + "\"");
    if (groovyHome.contains("grails")) { //a bit of a hack
      params.getVMParametersList().addParametersString("-Dgrails.home=" + "\"" + groovyHome + "\"");
    }
    if (groovyHome.contains("griffon")) { //a bit of a hack
      params.getVMParametersList().addParametersString("-Dgriffon.home=" + "\"" + groovyHome + "\"");
    }
  }

  protected static void setToolsJar(JavaParameters params) {
    Sdk jdk = params.getJdk();
    if (jdk != null && jdk.getSdkType() instanceof JavaSdkType) {
      String toolsPath = ((JavaSdkType)jdk.getSdkType()).getToolsPath(jdk);
      if (toolsPath != null) {
        params.getVMParametersList().add("-Dtools.jar=" + toolsPath);
      }
    }
  }

  @Nullable
  protected static VirtualFile findGroovyJar(@NotNull Module module) {
    final Pattern pattern = Pattern.compile(".*[\\\\/]groovy[^\\\\/]*jar");
    for (Library library : GroovyConfigUtils.getInstance().getSDKLibrariesByModule(module)) {
      for (VirtualFile root : library.getFiles(OrderRootType.CLASSES)) {
        if (pattern.matcher(root.getPresentableUrl()).matches()) {
          return root;
        }
      }
    }
    return null;
  }

  protected static void addClasspathFromRootModel(@Nullable Module module, boolean isTests, JavaParameters params) throws CantRunException {
    if (module == null) {
      return;
    }

    final JavaParameters tmp = new JavaParameters();
    tmp.configureByModule(module, isTests ? JavaParameters.JDK_AND_CLASSES_AND_TESTS : JavaParameters.JDK_AND_CLASSES);
    StringBuffer buffer = RunnerUtil.getClearClassPathString(tmp, module);
    if (buffer.length() > 0) {
      params.getProgramParametersList().add("--classpath");
      params.getProgramParametersList().add(buffer.toString());
    }
  }

}
