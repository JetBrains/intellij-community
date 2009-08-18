package org.jetbrains.plugins.groovy.runner;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.configurations.RunConfigurationModule;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;
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
}
