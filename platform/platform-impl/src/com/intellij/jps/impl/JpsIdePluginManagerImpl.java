// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jps.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.jetbrains.jps.model.module.UnknownSourceRootType;
import org.jetbrains.jps.model.module.UnknownSourceRootTypeProperties;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer;
import org.jetbrains.jps.model.serialization.module.JpsModuleSourceRootPropertiesSerializer;
import org.jetbrains.jps.model.serialization.module.UnknownSourceRootPropertiesSerializer;
import org.jetbrains.jps.plugin.JpsPluginManager;
import org.jetbrains.jps.service.JpsServiceManager;
import org.jetbrains.jps.service.impl.JpsServiceManagerImpl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public final class JpsIdePluginManagerImpl extends JpsPluginManager {
  private final List<PluginDescriptor> myExternalBuildPlugins = new CopyOnWriteArrayList<>();
  private final AtomicInteger myModificationStamp = new AtomicInteger(0);
  private final boolean myFullyLoaded;

  public JpsIdePluginManagerImpl() {
    Application application = ApplicationManager.getApplication();
    myFullyLoaded = application != null;
    if (!myFullyLoaded) {
      //this may happen e.g. in tests if some test is executed before Application is initialized; in that case the created instance won't be cached
      //and will be reinitialized next time
      return;
    }

    ExtensionsArea rootArea = application.getExtensionArea();
    //todo[nik] get rid of this check: currently this class is used in intellij.platform.jps.build tests instead of JpsPluginManagerImpl because intellij.platform.ide.impl module is added to classpath via testFramework
    if (rootArea.hasExtensionPoint(JpsPluginBean.EP_NAME)) {
      final Ref<Boolean> initial = new Ref<>(Boolean.TRUE);
      JpsPluginBean.EP_NAME.getPoint().addExtensionPointListener(new ExtensionPointListener<JpsPluginBean>() {
        @Override
        public void extensionAdded(@NotNull JpsPluginBean extension, @NotNull PluginDescriptor pluginDescriptor) {
          if (initial.get()) {
            myExternalBuildPlugins.add(pluginDescriptor);
          }
          else {
            handlePluginAdded(pluginDescriptor);
          }
        }

        @Override
        public void extensionRemoved(@NotNull JpsPluginBean extension, @NotNull PluginDescriptor pluginDescriptor) {
          handlePluginRemoved(pluginDescriptor);
        }
      }, true, null);
      initial.set(Boolean.FALSE);
    }
    if (rootArea.hasExtensionPoint("com.intellij.compileServer.plugin")) {
      ExtensionPoint extensionPoint = rootArea.getExtensionPoint("com.intellij.compileServer.plugin");
      final Ref<Boolean> initial = new Ref<>(Boolean.TRUE);
      //noinspection unchecked
      extensionPoint.addExtensionPointListener(new ExtensionPointListener() {
        @Override
        public void extensionAdded(@NotNull Object extension, @NotNull PluginDescriptor pluginDescriptor) {
          if (initial.get()) {
            myExternalBuildPlugins.add(pluginDescriptor);
          }
          else {
            handlePluginAdded(pluginDescriptor);
          }
        }

        @Override
        public void extensionRemoved(@NotNull Object extension, @NotNull PluginDescriptor pluginDescriptor) {
          handlePluginRemoved(pluginDescriptor);
        }
      }, true, null);
      initial.set(Boolean.FALSE);
    }
  }

  @Override
  public boolean isFullyLoaded() {
    return myFullyLoaded;
  }

  private void handlePluginRemoved(@NotNull PluginDescriptor pluginDescriptor) {
    if (!myExternalBuildPlugins.contains(pluginDescriptor)) return;

    Map<JpsModuleSourceRootType<?>, JpsModuleSourceRootPropertiesSerializer<?>> removed = new HashMap<>();
    for (JpsModelSerializerExtension extension : loadExtensions(JpsModelSerializerExtension.class)) {
      for (JpsModuleSourceRootPropertiesSerializer<?> serializer : extension.getModuleSourceRootPropertiesSerializers()) {
        removed.put(serializer.getType(), serializer);
      }
    }

    for (JpsModelSerializerExtension extension : loadExtensions(JpsModelSerializerExtension.class, descriptor -> !descriptor.equals(pluginDescriptor))) {
      for (JpsModuleSourceRootPropertiesSerializer<?> serializer : extension.getModuleSourceRootPropertiesSerializers()) {
        removed.remove(serializer.getType());
      }
    }

    if (!removed.isEmpty()) {
      for (Project project : ProjectManager.getInstance().getOpenProjects()) {
        replaceWithUnknownRootType(project, removed.values());
      }
    }
    myExternalBuildPlugins.remove(pluginDescriptor);
    myModificationStamp.incrementAndGet();
    JpsServiceManager jpsServiceManager = JpsServiceManager.getInstance();
    if (jpsServiceManager instanceof JpsServiceManagerImpl) {
      ((JpsServiceManagerImpl)jpsServiceManager).cleanupExtensionCache();
    }
  }

  private void handlePluginAdded(@NotNull PluginDescriptor pluginDescriptor) {
    if (myExternalBuildPlugins.contains(pluginDescriptor)) {
      return;
    }
    Set<String> before = new HashSet<>();
    for (JpsModelSerializerExtension extension : loadExtensions(JpsModelSerializerExtension.class)) {
      for (JpsModuleSourceRootPropertiesSerializer<?> serializer : extension.getModuleSourceRootPropertiesSerializers()) {
        before.add(serializer.getTypeId());
      }
    }

    myExternalBuildPlugins.add(pluginDescriptor);
    myModificationStamp.incrementAndGet();

    Map<String, JpsModuleSourceRootPropertiesSerializer<?>> added = new HashMap<>();
    for (JpsModelSerializerExtension extension : loadExtensions(JpsModelSerializerExtension.class)) {
      for (JpsModuleSourceRootPropertiesSerializer<?> serializer : extension.getModuleSourceRootPropertiesSerializers()) {
        added.put(serializer.getTypeId(), serializer);
      }
    }
    added.keySet().removeAll(before);
    
    if (!added.isEmpty()) {
      for (Project project : ProjectManager.getInstance().getOpenProjects()) {
        updateCustomRootTypes(project, added.values());
      }
    }
  }

  private static void replaceWithUnknownRootType(Project project, Collection<JpsModuleSourceRootPropertiesSerializer<?>> unregisteredSerializers) {
    if (unregisteredSerializers.isEmpty()) {
      return;
    }
    Map<JpsModuleSourceRootType<?>, JpsModuleSourceRootPropertiesSerializer<?>> serializers = new HashMap<>();
    for (JpsModuleSourceRootPropertiesSerializer<?> serializer : unregisteredSerializers) {
      serializers.put(serializer.getType(), serializer);
    }
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      ModuleRootModificationUtil.modifyModel(module, model -> {
        boolean shouldCommit = false;
        for (ContentEntry contentEntry : model.getContentEntries()) {
          for (SourceFolder folder : contentEntry.getSourceFolders()) {
            JpsModuleSourceRootPropertiesSerializer<?> removedSerializer = serializers.get(folder.getRootType());
            if (removedSerializer != null) {
              changeType(
                folder,
                UnknownSourceRootPropertiesSerializer.forType(removedSerializer.getTypeId()),
                serializeProperties(folder, removedSerializer)
              );
              shouldCommit = true;
            }
          }
        }
        return shouldCommit;
      });
    }
  }

  private static void updateCustomRootTypes(Project project, Collection<JpsModuleSourceRootPropertiesSerializer<?>> registeredSerializers) {
    if (registeredSerializers.isEmpty()) {
      return;
    }
    Map<String, JpsModuleSourceRootPropertiesSerializer<?>> serializers = new HashMap<>();
    for (JpsModuleSourceRootPropertiesSerializer<?> ser : registeredSerializers) {
      serializers.put(ser.getTypeId(), ser);
    }
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      Map<SourceFolder, Pair<JpsModuleSourceRootPropertiesSerializer<?>, Element>> foldersToUpdate = new HashMap<>();
      for (ContentEntry contentEntry : ModuleRootManager.getInstance(module).getContentEntries()) {
        for (SourceFolder folder : contentEntry.getSourceFolders()) {
          if (folder.getRootType() instanceof UnknownSourceRootType) {
            UnknownSourceRootType type = (UnknownSourceRootType)folder.getRootType();
            JpsModuleSourceRootPropertiesSerializer<?> serializer = serializers.get(type.getUnknownTypeId());
            if (serializer != null) {
              UnknownSourceRootTypeProperties<?> properties = folder.getJpsElement().getProperties(type);
              Object data = properties != null ? properties.getPropertiesData() : null;
              foldersToUpdate.put(folder, new Pair<>(serializer, data instanceof Element ? (Element)data : null));
            }
          }
        }
      }
      if (!foldersToUpdate.isEmpty()) {
        ModuleRootModificationUtil.updateModel(module, model -> {
          for (ContentEntry contentEntry : model.getContentEntries()) {
            for (SourceFolder folder : contentEntry.getSourceFolders()) {
              Pair<JpsModuleSourceRootPropertiesSerializer<?>, Element> pair = foldersToUpdate.get(folder);
              if (pair != null) {
                changeType(folder, pair.first, pair.second);
              }
            }
          }
        });
      }
    }
  }

  @Nullable
  private static <P extends JpsElement> Element serializeProperties(SourceFolder root, @NotNull JpsModuleSourceRootPropertiesSerializer<P> serializer) {
    P properties = root.getJpsElement().getProperties(serializer.getType());
    if (properties != null) {
      Element sourceElement = new Element(JpsModuleRootModelSerializer.SOURCE_FOLDER_TAG);
      serializer.saveProperties(properties, sourceElement);
      return sourceElement;
    }
    return null;
  }

  private static <P extends JpsElement> void changeType(SourceFolder root, @NotNull JpsModuleSourceRootPropertiesSerializer<P> serializer, @Nullable Element serializedProps) {
    root.changeType(
      serializer.getType(),
      serializedProps != null ? serializer.loadProperties(serializedProps) : serializer.getType().createDefaultProperties()
    );
  }

  @Override
  public int getModificationStamp() {
    if (!myFullyLoaded && myModificationStamp.get() == 0 && ApplicationManager.getApplication() != null) {
      myModificationStamp.compareAndSet(0, 1);
    }
    return myModificationStamp.get();
  }

  @NotNull
  @Override
  public <T> Collection<T> loadExtensions(@NotNull Class<T> extensionClass) {
    return loadExtensions(extensionClass, null);
  }

  @NotNull
  private <T> Collection<T> loadExtensions(@NotNull Class<T> extensionClass, @Nullable Predicate<PluginDescriptor> filter) {
    Set<ClassLoader> loaders = new LinkedHashSet<>();
    for (PluginDescriptor plugin : myExternalBuildPlugins) {
      if (filter == null || filter.test(plugin)) {
        ContainerUtil.addIfNotNull(loaders, plugin.getPluginClassLoader());
      }
    }
    if (loaders.isEmpty()) {
      loaders.add(getClass().getClassLoader());
    }
    return loadExtensionsFrom(loaders, extensionClass);
  }

  @NotNull
  private static <T> Collection<T> loadExtensionsFrom(@NotNull Collection<ClassLoader> loaders, @NotNull Class<T> extensionClass) {
    if (loaders.isEmpty()) {
      return Collections.emptyList();
    }
    String resourceName = "META-INF/services/" + extensionClass.getName();
    Set<Class<T>> classes = new LinkedHashSet<>();
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
