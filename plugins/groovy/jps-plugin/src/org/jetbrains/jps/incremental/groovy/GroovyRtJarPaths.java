// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.groovy;

import com.intellij.openapi.application.PathManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class GroovyRtJarPaths {
  public static List<String> getGroovyRtRoots(File jpsPluginRoot, boolean addClassLoaderJar) {
    List<String> result = new ArrayList<>();
    addGroovyRtJarPath(jpsPluginRoot, "groovy-rt.jar",
                       Collections.singletonList("intellij.groovy.rt"), "groovy-rt", result);
    addGroovyRtJarPath(jpsPluginRoot, "groovy-constants-rt.jar",
                       Collections.singletonList("intellij.groovy.constants.rt"), "groovy-constants-rt", result);
    if (addClassLoaderJar) {
      addGroovyRtJarPath(jpsPluginRoot, "groovy-rt-class-loader.jar",
                         Collections.singletonList("intellij.groovy.rt.classLoader"), "groovy-rt-class-loader", result);
    }
    return result;
  }

  private static void addGroovyRtJarPath(File jpsPluginClassesRoot,
                                         String jarNameInDistribution,
                                         List<String> moduleNames,
                                         String mavenArtifactNamePrefix,
                                         List<String> to) {
    File parentDir = jpsPluginClassesRoot.getParentFile();
    if (jpsPluginClassesRoot.isFile()) {
      String relevantJarsRoot = PathManager.getArchivedCompliedClassesLocation();
      if (relevantJarsRoot != null && jpsPluginClassesRoot.getAbsolutePath().startsWith(relevantJarsRoot)) {
        // running from archived compilation output
        Map<String, String> mapping = PathManager.getArchivedCompiledClassesMapping();
        if (mapping == null) {
          throw new IllegalStateException("Mapping cannot be null at this point. 'intellij.test.jars.location' is not null");
        }
        for (String moduleName : moduleNames) {
          String path = mapping.get("production/" + moduleName);
          if (path == null) {
            throw new IllegalStateException("Mapping for module '" + moduleName + "' not found in " + mapping);
          }
          to.add(path);
        }
        return;
      }
      String fileName;
      if (jpsPluginClassesRoot.getName().equals("groovy-jps.jar")) {
        fileName = jarNameInDistribution;
      }
      else {
        String name = jpsPluginClassesRoot.getName();
        int dotIndex = name.lastIndexOf('.');
        name = dotIndex < 0 ? name : name.substring(0, dotIndex);
        int dashIndex = name.lastIndexOf('-');
        String version = dashIndex < 0 ? null : name.substring(dashIndex + 1);
        fileName = mavenArtifactNamePrefix + "-" + version + ".jar";
        if (parentDir.getName().equals(version)) {
          parentDir = new File(parentDir.getParentFile().getParentFile(), mavenArtifactNamePrefix + "/" + version);
        }
      }
      to.add(new File(parentDir, fileName).getPath());
    }
    else {
      for (String moduleName : moduleNames) {
        to.add(new File(parentDir, moduleName).getPath());
      }
    }
  }
}
