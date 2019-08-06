// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jps.impl;

import com.intellij.openapi.extensions.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.plugin.JpsPluginManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author nik
 */
public final class JpsIdePluginManagerImpl extends JpsPluginManager {
  private final List<PluginDescriptor> myExternalBuildPlugins = new CopyOnWriteArrayList<>();

  public JpsIdePluginManagerImpl() {
    ExtensionsArea rootArea = Extensions.getRootArea();
    if (rootArea == null) {
      return;
    }

    //todo[nik] get rid of this check: currently this class is used in intellij.platform.jps.build tests instead of JpsPluginManagerImpl because intellij.platform.ide.impl module is added to classpath via testFramework
    if (rootArea.hasExtensionPoint(JpsPluginBean.EP_NAME)) {
      JpsPluginBean.EP_NAME.getPoint(null).addExtensionPointListener(new ExtensionPointListener<JpsPluginBean>() {
        @Override
        public void extensionAdded(@NotNull JpsPluginBean extension, @NotNull PluginDescriptor pluginDescriptor) {
          ContainerUtil.addIfNotNull(myExternalBuildPlugins, pluginDescriptor);
        }
      }, true, null);
    }
    if (rootArea.hasExtensionPoint("com.intellij.compileServer.plugin")) {
      ExtensionPoint extensionPoint = rootArea.getExtensionPoint("com.intellij.compileServer.plugin");
      //noinspection unchecked
      extensionPoint.addExtensionPointListener(new ExtensionPointListener() {
        @Override
        public void extensionAdded(@NotNull Object extension, @NotNull PluginDescriptor pluginDescriptor) {
          ContainerUtil.addIfNotNull(myExternalBuildPlugins, pluginDescriptor);
        }
      });
    }
  }

  @NotNull
  @Override
  public <T> Collection<T> loadExtensions(@NotNull Class<T> extensionClass) {
    String resourceName = "META-INF/services/" + extensionClass.getName();
    Set<Class<T>> classes = new LinkedHashSet<>();
    Set<ClassLoader> loaders = new LinkedHashSet<>();
    for (PluginDescriptor plugin : myExternalBuildPlugins) {
      ContainerUtil.addIfNotNull(loaders, plugin.getPluginClassLoader());
    }
    if (loaders.isEmpty()) {
      loaders.add(getClass().getClassLoader());
    }

    Set<String> loadedUrls = new HashSet<>();
    for (ClassLoader loader : loaders) {
      try {
        Enumeration<URL> resources = loader.getResources(resourceName);
        while (resources.hasMoreElements()) {
          URL url = resources.nextElement();
          if (loadedUrls.add(url.toExternalForm())) {
            loadImplementations(url, loader, classes);
          }
        }
      }
      catch (IOException e) {
        throw new ServiceConfigurationError("Cannot load configuration files for " + extensionClass.getName(), e);
      }
    }
    List<T> extensions = new ArrayList<>();
    for (Class<T> aClass : classes) {
      try {
        extensions.add(extensionClass.cast(aClass.newInstance()));
      }
      catch (Exception e) {
        throw new ServiceConfigurationError("Class " + aClass.getName() + " cannot be instantiated", e);
      }
    }
    return extensions;
  }

  private static <T> void loadImplementations(URL url, ClassLoader loader, Set<? super Class<T>> result) throws IOException {
    for (String name : loadClassNames(url)) {
      try {
        //noinspection unchecked
        result.add((Class<T>)Class.forName(name, false, loader));
      }
      catch (ClassNotFoundException e) {
        throw new ServiceConfigurationError("Cannot find class " + name, e);
      }
    }
  }

  private static List<String> loadClassNames(URL url) throws IOException {
    List<String> result = new ArrayList<>();
    try (BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = in.readLine()) != null) {
        int i = line.indexOf('#');
        if (i >= 0) line = line.substring(0, i);
        line = line.trim();
        if (!line.isEmpty()) {
          result.add(line);
        }
      }
    }
    return result;
  }
}
