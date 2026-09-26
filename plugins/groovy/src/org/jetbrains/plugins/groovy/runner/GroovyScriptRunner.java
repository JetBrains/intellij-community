// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.runner;

import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.JavaSdkType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.PathsList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class GroovyScriptRunner {

  private static final ConcurrentHashMap<String, Path> EXTRACTED_CONF_CACHE = new ConcurrentHashMap<>();

  public abstract boolean isValidModule(@NotNull Module module);

  public abstract void ensureRunnerConfigured(@NotNull GroovyScriptRunConfiguration configuration) throws RuntimeConfigurationException;

  public abstract void configureCommandLine(JavaParameters params, @Nullable Module module, boolean tests, VirtualFile script,
                                            GroovyScriptRunConfiguration configuration) throws CantRunException;

  public boolean shouldRefreshAfterFinish() {
    return false;
  }

  protected static @Nullable String getConfPath(final String groovyHomePath) {
    String confpath = FileUtil.toSystemDependentName(groovyHomePath + "/conf/groovy-starter.conf");
    if (new File(confpath).exists()) {
      return confpath;
    }
    return null;
  }

  public static String getPathInConf(String fileName) {
    final Path jarPath = Path.of(PathUtil.getJarPathForClass(GroovyLanguage.class));

    if (Files.isRegularFile(jarPath)) { //jar; distribution or dev-run jar-cache mode
      Path candidate = jarPath.resolveSibling(fileName).normalize().toAbsolutePath();
      if (Files.exists(candidate)) return candidate.toString();
      // jar-cache / Bazel dev: script is inside the JAR
      return EXTRACTED_CONF_CACHE.computeIfAbsent(fileName, name -> {
        URL resource = GroovyLanguage.class.getResource("/conf/" + name);
        if (resource == null) throw new RuntimeException("Groovy resource not found: /conf/" + name);
        try {
          Path tempFile = Files.createTempFile("groovy_conf_", "_" + name);
          tempFile.toFile().deleteOnExit();
          try (InputStream in = resource.openStream()) {
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
          }
          return tempFile;
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }).toString();
    }

    //else, it's directory in out, development mode
    return jarPath.resolve("conf").resolve(fileName).normalize().toAbsolutePath().toString();
  }

  public static void setGroovyHome(JavaParameters params, @NotNull String groovyHome) {
    params.getVMParametersList().add("-Dgroovy.home=" + groovyHome);
    if (groovyHome.contains("grails")) { //a bit of a hack
      params.getVMParametersList().add("-Dgrails.home=" + groovyHome);
    }
    if (groovyHome.contains("griffon")) { //a bit of a hack
      params.getVMParametersList().add("-Dgriffon.home=" + groovyHome);
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

  protected static @Nullable VirtualFile findGroovyJar(@NotNull Module module) {
    final VirtualFile[] files = OrderEnumerator.orderEntries(module).getAllLibrariesAndSdkClassesRoots();
    for (VirtualFile root : files) {
      if (GroovyConfigUtils.GROOVY_JAR_PATTERN.matcher(root.getName()).matches() || GroovyConfigUtils.matchesGroovyAll(root.getName())) {
        return root;
      }
    }
    for (VirtualFile file : files) {
      if (file.getName().contains("groovy") && "jar".equals(file.getExtension())) {
        return file;
      }
    }
    return null;
  }

  protected static void addClasspathFromRootModel(@Nullable Module module, boolean isTests, JavaParameters params, boolean allowDuplication) throws CantRunException {
    PathsList nonCore = new PathsList();
    getClassPathFromRootModel(module, isTests, params, allowDuplication, nonCore);

    final String cp = nonCore.getPathsString();
    if (!StringUtil.isEmptyOrSpaces(cp)) {
      params.getProgramParametersList().add("--classpath");
      params.getProgramParametersList().add(cp);
    }
  }

  public static @Nullable PathsList getClassPathFromRootModel(Module module,
                                                              boolean isTests,
                                                              JavaParameters params,
                                                              boolean allowDuplication,
                                                              PathsList pathList)
    throws CantRunException {
    if (module == null) {
      return null;
    }

    pathList.add(".");

    final JavaParameters tmp = new JavaParameters();
    tmp.configureByModule(module, isTests ? JavaParameters.CLASSES_AND_TESTS : JavaParameters.CLASSES_ONLY);
    if (tmp.getClassPath().getVirtualFiles().isEmpty()) {
      return null;
    }

    Set<VirtualFile> core = new HashSet<>(params.getClassPath().getVirtualFiles());

    for (VirtualFile virtualFile : tmp.getClassPath().getVirtualFiles()) {
      if (allowDuplication || !core.contains(virtualFile)) {
        pathList.add(virtualFile);
      }
    }
    return pathList;
  }
}
