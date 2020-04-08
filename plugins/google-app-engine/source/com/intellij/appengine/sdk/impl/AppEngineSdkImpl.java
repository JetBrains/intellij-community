// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.appengine.sdk.impl;

import com.intellij.appengine.sdk.AppEngineSdk;
import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.JarUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.appengine.model.impl.JpsAppEngineModuleExtensionImpl;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.Attributes;

public class AppEngineSdkImpl implements AppEngineSdk {
  private static final Logger LOG = Logger.getInstance(AppEngineSdkImpl.class);
  private Map<String, Set<String>> myClassesWhiteList;
  private Map<String, Set<String>> myMethodsBlackList;
  private final String myHomePath;

  public AppEngineSdkImpl(String homePath) {
    myHomePath = homePath;
  }

  @Override
  @NotNull
  public File getAppCfgFile() {
    final String extension = SystemInfo.isWindows ? "cmd" : "sh";
    return new File(myHomePath, "bin/appcfg." + extension);
  }

  @Override
  @NotNull
  public File getWebSchemeFile() {
    return new File(myHomePath, "docs/appengine-web.xsd");
  }

  @NotNull
  @Override
  public File getApplicationSchemeFile() {
    return new File(myHomePath, "docs/appengine-application.xsd");
  }

  @Override
  @NotNull
  public File getToolsApiJarFile() {
    return new File(myHomePath, JpsAppEngineModuleExtensionImpl.LIB_APPENGINE_TOOLS_API_JAR);
  }

  @Override
  public File @NotNull [] getLibraries() {
    return getJarsFromDirectory(new File(myHomePath, "lib/shared"));
  }

  @Override
  public File @NotNull [] getJspLibraries() {
    return getJarsFromDirectory(new File(myHomePath, "lib/shared/jsp"));
  }

  @Override
  public void patchJavaParametersForDevServer(@NotNull ParametersList vmParameters) {
    final String agentPath = myHomePath + "/lib/agent/appengine-agent.jar";
    if (new File(FileUtil.toSystemDependentName(agentPath)).exists()) {
      vmParameters.add("-javaagent:" + agentPath);
    }
    String patchPath = myHomePath + "/lib/override/appengine-dev-jdk-overrides.jar";
    if (new File(FileUtil.toSystemDependentName(patchPath)).exists()) {
      vmParameters.add("-Xbootclasspath/p:" + patchPath);
    }
  }

  private static File[] getJarsFromDirectory(File libFolder) {
    List<File> jars = new ArrayList<>();
    final File[] files = libFolder.listFiles();
    if (files != null) {
      for (File file : files) {
        if (file.isFile() && file.getName().endsWith(".jar")) {
          jars.add(file);
        }
      }
    }
    return jars.toArray(new File[0]);
  }

  @Override
  @NotNull
  public String getSdkHomePath() {
    return myHomePath;
  }

  @Override
  public boolean isClassInWhiteList(@NotNull String className) {
    if (!isValid()) return true;

    if (myClassesWhiteList == null) {
      File cachedWhiteList = getCachedWhiteListFile();
      if (cachedWhiteList.exists()) {
        try {
          myClassesWhiteList = AppEngineSdkUtil.loadWhiteList(cachedWhiteList);
        }
        catch (IOException e) {
          LOG.error(e);
          myClassesWhiteList = Collections.emptyMap();
        }
      }
      else {
        myClassesWhiteList = AppEngineSdkUtil.computeWhiteList(getToolsApiJarFile());
        if (!myClassesWhiteList.isEmpty()) {
          AppEngineSdkUtil.saveWhiteList(cachedWhiteList, myClassesWhiteList);
        }
      }
    }
    if (myClassesWhiteList.isEmpty()) {
      //don't report errors if white-list wasn't properly loaded
      return true;
    }

    final String packageName = StringUtil.getPackageName(className);
    final String name = StringUtil.getShortName(className);
    final Set<String> classes = myClassesWhiteList.get(packageName);
    return classes != null && classes.contains(name);
  }

  @Override
  @Nullable
  public String getVersion() {
    return JarUtil.getJarAttribute(getToolsApiJarFile(), "com/google/appengine/tools/info/", Attributes.Name.SPECIFICATION_VERSION);
  }

  private File getCachedWhiteListFile() {
    String fileName = StringUtil.getShortName(myHomePath, '/') + Integer.toHexString(myHomePath.hashCode()) + "_" + Long.toHexString(getToolsApiJarFile().lastModified());
    return new File(AppEngineUtil.getAppEngineSystemDir(), fileName);
  }

  @Override
  public boolean isMethodInBlacklist(@NotNull String className, @NotNull String methodName) {
    if (myMethodsBlackList == null) {
      try {
        myMethodsBlackList = loadBlackList();
      }
      catch (IOException e) {
        LOG.error(e);
        myMethodsBlackList = new THashMap<>();
      }
    }
    final Set<String> methods = myMethodsBlackList.get(className);
    return methods != null && methods.contains(methodName);
  }

  @Override
  public boolean isValid() {
    return getToolsApiJarFile().exists() && getAppCfgFile().exists();
  }

  @Override
  @NotNull
  public String getOrmLibDirectoryPath() {
    return getLibUserDirectoryPath() + "/orm";
  }

  @NotNull
  @Override
  public List<String> getUserLibraryPaths() {
    List<String> result = new ArrayList<>();
    result.add(getLibUserDirectoryPath());
    File opt = new File(myHomePath, "lib/opt/user");
    ContainerUtil.addIfNotNull(result, findLatestVersion(new File(opt, "appengine-endpoints")));
    ContainerUtil.addIfNotNull(result, findLatestVersion(new File(opt, "jsr107")));
    return result;
  }

  private static String findLatestVersion(File dir) {
    String[] names = dir.list();
    if (names != null && names.length > 0) {
      String max = Collections.max(Arrays.asList(names));
      return FileUtil.toSystemIndependentName(new File(dir, max).getAbsolutePath());
    }
    return null;
  }

  @Override
  public VirtualFile @NotNull [] getOrmLibSources() {
    final File libsDir = new File(myHomePath, "src/orm");
    final File[] files = libsDir.listFiles();
    List<VirtualFile> roots = new ArrayList<>();
    if (files != null) {
      for (File file : files) {
        final String url = VfsUtil.getUrlForLibraryRoot(file);
        final VirtualFile zipRoot = VirtualFileManager.getInstance().findFileByUrl(url);
        if (zipRoot != null && zipRoot.isDirectory()) {
          String fileName = file.getName();
          final String srcDirName = StringUtil.trimEnd(fileName, "-src.zip");
          final VirtualFile sourcesDir = zipRoot.findFileByRelativePath(srcDirName + "/src/java");
          if (sourcesDir != null) {
            roots.add(sourcesDir);
          }
          else {
            roots.add(zipRoot);
          }
        }
      }
    }
    return VfsUtilCore.toVirtualFileArray(roots);
  }

  public String getLibUserDirectoryPath() {
    return myHomePath + "/lib/user";
  }

  private Map<String, Set<String>> loadBlackList() throws IOException {
    final InputStream stream = getClass().getResourceAsStream("/data/methodsBlacklist.txt");
    LOG.assertTrue(stream != null, "/data/methodsBlacklist.txt not found");
    final THashMap<String, Set<String>> map = new THashMap<>();
    BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        final int i = line.indexOf(':');
        String className = line.substring(0, i);
        String methods = line.substring(i + 1);
        map.put(className, new THashSet<>(StringUtil.split(methods, ",")));
      }
    }
    finally {
      reader.close();
    }
    return map;
  }
}
