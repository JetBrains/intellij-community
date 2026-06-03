// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.impl;

import com.intellij.platform.runtime.repository.MalformedRepositoryException;
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor;
import com.intellij.platform.runtime.repository.RuntimeModuleHeader;
import com.intellij.platform.runtime.repository.RuntimeModuleId;
import com.intellij.platform.runtime.repository.RuntimeModuleRepository;
import com.intellij.platform.runtime.repository.RuntimePluginHeader;
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor;
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleRepositoryData;
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RuntimeModuleRepositoryImpl implements RuntimeModuleRepository {
  private static final RuntimeModuleHeaderImpl UNRESOLVED_HEADER = new RuntimeModuleHeaderImpl(
    RuntimeModuleId.raw("unresolved", RuntimeModuleId.DEFAULT_NAMESPACE), Path.of(""), Collections.emptyList(), Collections.emptyList()
  );
  private final Map<RuntimeModuleId, ResolveResult> myResolveResults;
  private final Map<RuntimeModuleId, RuntimeModuleHeaderImpl> myHeadersCache;
  private volatile Map<RuntimeModuleId, RuntimePluginHeader> myBundledPluginHeadersByDescriptorModule;
  private volatile RawRuntimeModuleRepositoryData myRawData;
  private final Path myDescriptorsFilePath;

  public RuntimeModuleRepositoryImpl(@NotNull Path descriptorsFilePath) {
    this(descriptorsFilePath, null);
  }

  public RuntimeModuleRepositoryImpl(@NotNull Path descriptorsFilePath, @Nullable RawRuntimeModuleRepositoryData preloadedRawData) {
    myDescriptorsFilePath = descriptorsFilePath;
    myResolveResults = new ConcurrentHashMap<>();
    myHeadersCache = new ConcurrentHashMap<>();
    myRawData = preloadedRawData;
  }

  @Override
  public @NotNull ResolveResult resolveModule(@NotNull RuntimeModuleId moduleId) {
    ResolveResult result = myResolveResults.get(moduleId);
    if (result == null) {
      result = resolveModule(moduleId, new LinkedHashMap<>());
    }
    return result;
  }

  @Override
  public @Nullable RuntimeModuleHeader findModuleHeader(@NotNull RuntimeModuleId moduleId) {
    return resolveHeader(moduleId);
  }

  private @Nullable RuntimeModuleHeaderImpl resolveHeader(@NotNull RuntimeModuleId moduleId) {
    RuntimeModuleHeaderImpl cached = myHeadersCache.get(moduleId);
    if (cached != null) return cached != UNRESOLVED_HEADER ? cached : null;

    RawRuntimeModuleRepositoryData rawData = getRawData();
    RawRuntimeModuleDescriptor rawDescriptor = rawData.findDescriptor(moduleId);
    if (rawDescriptor == null && moduleId.getNamespace().endsWith(RuntimeModuleId.LEGACY_JPS_MODULE_NAMESPACE_SUFFIX)) {
      /* we don't have syntax to specify namespace in product-modules.xml, so currently all elements are supposed to be from the
         LEGACY_JPS_MODULE_NAMESPACE, but they actually can be registered as content modules */
      rawDescriptor = rawData.findDescriptor(RuntimeModuleId.contentModule(moduleId.getName(), RuntimeModuleId.DEFAULT_NAMESPACE));
    }
    if (rawDescriptor != null) {
      var header = new RuntimeModuleHeaderImpl(rawDescriptor.getModuleId(), rawData.getBasePath(), rawDescriptor.getResourcePaths(), rawDescriptor.getDependencyIds());
      myHeadersCache.put(moduleId, header);
      return header;
    }
    else {
      myHeadersCache.put(moduleId, UNRESOLVED_HEADER);
      return null;
    }
  }

  private @NotNull ResolveResult resolveModule(@NotNull RuntimeModuleId moduleId, @NotNull Map<RuntimeModuleId, RuntimeModuleDescriptorImpl> dependencyPath) {
    ResolveResult cached = myResolveResults.get(moduleId);
    if (cached != null) return cached;

    var header = resolveHeader(moduleId);
    if (header == null) {
      List<RuntimeModuleId> failedPath = new ArrayList<>(dependencyPath.size() + 1);
      failedPath.addAll(dependencyPath.keySet());
      failedPath.add(moduleId);
      return new FailedResolveResult(failedPath);
    }
    
    List<RuntimeModuleId> rawDependencies = header.getDependencies();
    List<RuntimeModuleDescriptor> resolvedDependencies = new ArrayList<>(rawDependencies.size());
    RuntimeModuleDescriptorImpl descriptor = new RuntimeModuleDescriptorImpl(header, resolvedDependencies);
    dependencyPath.put(header.getModuleId(), descriptor);
    for (RuntimeModuleId dependencyId : rawDependencies) {
      RuntimeModuleDescriptor circularDependency = dependencyPath.get(dependencyId);
      if (circularDependency != null) {
        //the circularDependency instance isn't fully constructed, but it's ok given that RuntimeModuleDescriptorImpl's constructor just stores the passed values 
        resolvedDependencies.add(circularDependency);
        continue;
      }
      
      ResolveResult result = resolveModule(dependencyId, dependencyPath);
      RuntimeModuleDescriptor module = result.getResolvedModule();
      if (module != null) {
        resolvedDependencies.add(module);
      }
      else {
        return result;
      }
    }
    dependencyPath.remove(moduleId);
    SuccessfulResolveResult result = new SuccessfulResolveResult(descriptor);
    myResolveResults.put(moduleId, result);
    return result;
  }

  @Override
  public @NotNull RuntimeModuleDescriptor getModule(@NotNull RuntimeModuleId moduleId) {
    ResolveResult result = resolveModule(moduleId);
    RuntimeModuleDescriptor module = result.getResolvedModule();
    if (module != null) {
      return module;
    }
    List<RuntimeModuleId> failedDependencyPath = result.getFailedDependencyPath();
    String message;
    if (failedDependencyPath.size() == 1) {
      message = "Cannot find module '" + failedDependencyPath.get(0).getDisplayName() + "'";
    }
    else {
      List<RuntimeModuleId> reversed = new ArrayList<>(failedDependencyPath.subList(0, failedDependencyPath.size() - 1));
      Collections.reverse(reversed);
      message = "Cannot resolve module '" + moduleId.getDisplayName() + "': module '" + failedDependencyPath.get(failedDependencyPath.size() - 1).getDisplayName() + "' (" +
                reversed.stream().map(id -> " <- '" + id.getDisplayName() + "'").collect(Collectors.joining()).trim() + ") is not found";
    }
    throw new MalformedRepositoryException(message);
  }

  @Override
  public @NotNull List<Path> getModuleResourcePaths(@NotNull RuntimeModuleId moduleId) {
    var header = resolveHeader(moduleId);
    if (header == null) {
      throw new MalformedRepositoryException("Cannot find module '" + moduleId.getDisplayName() + "'");
    }
    return header.getOwnClasspath();
  }

  @Override
  public @NotNull List<@NotNull Path> getBootstrapClasspath(@NotNull String bootstrapModuleName) {
    if (myRawData == null) {
      try {
        String[] bootstrapClasspath = RuntimeModuleRepositorySerialization.loadBootstrapClasspath(myDescriptorsFilePath, bootstrapModuleName);
        if (bootstrapClasspath != null) {
          List<Path> result = new ArrayList<>(bootstrapClasspath.length);
          for (String relativePath : bootstrapClasspath) {
            result.add(myDescriptorsFilePath.getParent().resolve(relativePath));
          }
          return result;
        }
      }
      catch (IOException ignore) {
      }
    }
    return getModule(RuntimeModuleId.legacyJpsModule(bootstrapModuleName)).getModuleClasspath();
  }

  @Override
  public @NotNull List<@NotNull RuntimePluginHeader> getBundledPluginHeaders() {
    return getRawData().getPluginHeaders();
  }

  @Override
  public @Nullable RuntimePluginHeader findBundledPluginHeader(@NotNull RuntimeModuleId pluginDescriptorModuleId) {
    if (myBundledPluginHeadersByDescriptorModule == null) {
      HashMap<RuntimeModuleId, RuntimePluginHeader> map = new HashMap<>();
      for (RuntimePluginHeader header : getRawData().getPluginHeaders()) {
        map.put(header.getPluginDescriptorModuleId(), header);
      }
      myBundledPluginHeadersByDescriptorModule = map;
    }
    RuntimePluginHeader header = myBundledPluginHeadersByDescriptorModule.get(pluginDescriptorModuleId);
    if (header == null && !pluginDescriptorModuleId.getNamespace().equals(RuntimeModuleId.DEFAULT_NAMESPACE)) {
      //some plugin descriptor modules are also registered as content modules with default namespace
      header = myBundledPluginHeadersByDescriptorModule.get(RuntimeModuleId.contentModule(pluginDescriptorModuleId.getName(), RuntimeModuleId.DEFAULT_NAMESPACE));
    }
    return header;
  }

  @Override
  public String toString() {
    return "RuntimeModuleRepository{descriptorsFilePath=" + myDescriptorsFilePath + '}';
  }

  private RawRuntimeModuleRepositoryData getRawData() {
    if (myRawData == null) {
      Path fallbackJarPath = RuntimeModuleRepositorySerialization.getFallbackJarPath(myDescriptorsFilePath);
      if (fallbackJarPath != null) {
        myRawData = RuntimeModuleRepositorySerialization.loadFromJar(fallbackJarPath);
      }
      else {
        myRawData = RuntimeModuleRepositorySerialization.loadFromCompactFile(myDescriptorsFilePath);
      }
    }
    return myRawData;
  }

  private static final class SuccessfulResolveResult implements ResolveResult {
    private final RuntimeModuleDescriptor myResolved;

    private SuccessfulResolveResult(@NotNull RuntimeModuleDescriptor resolved) {
      myResolved = resolved;
    }

    @Override
    public @NotNull RuntimeModuleDescriptor getResolvedModule() {
      return myResolved;
    }

    @Override
    public @NotNull List<RuntimeModuleId> getFailedDependencyPath() {
      return Collections.emptyList();
    }
  }
  
  private static final class FailedResolveResult implements ResolveResult {
    private final List<RuntimeModuleId> myFailedDependencyPath;

    private FailedResolveResult(@NotNull List<RuntimeModuleId> failedDependencyPath) {
      myFailedDependencyPath = failedDependencyPath; 
    }

    @Override
    public @Nullable RuntimeModuleDescriptor getResolvedModule() {
      return null;
    }

    @Override
    public @NotNull List<RuntimeModuleId> getFailedDependencyPath() {
      return myFailedDependencyPath;
    }
  }
}
