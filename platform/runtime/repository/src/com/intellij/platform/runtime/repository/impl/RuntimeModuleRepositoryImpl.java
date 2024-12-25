// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.impl;

import com.intellij.platform.runtime.repository.MalformedRepositoryException;
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor;
import com.intellij.platform.runtime.repository.RuntimeModuleId;
import com.intellij.platform.runtime.repository.RuntimeModuleRepository;
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor;
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleRepositoryData;
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization;
import com.intellij.platform.runtime.repository.serialization.impl.JarFileSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RuntimeModuleRepositoryImpl implements RuntimeModuleRepository {
  private final Map<RuntimeModuleId, ResolveResult> myResolveResults;
  private volatile RawRuntimeModuleRepositoryData myMainData;
  private volatile List<RawRuntimeModuleRepositoryData> myAdditionalData;
  private final Path myDescriptorsJarPath;
  private final Map<String, RuntimeModuleId> myInternedModuleIds;

  public RuntimeModuleRepositoryImpl(@NotNull Path descriptorsJarPath) {
    this(descriptorsJarPath, null);
  }

  public RuntimeModuleRepositoryImpl(@NotNull Path descriptorsJarPath, @Nullable RawRuntimeModuleRepositoryData preloadedMainData) {
    myDescriptorsJarPath = descriptorsJarPath;
    myResolveResults = new ConcurrentHashMap<>();
    myInternedModuleIds = new ConcurrentHashMap<>();
    myMainData = preloadedMainData;
  }

  @Override
  public @NotNull ResolveResult resolveModule(@NotNull RuntimeModuleId moduleId) {
    ResolveResult result = myResolveResults.get(moduleId);
    if (result == null) {
      result = resolveModule(moduleId, new LinkedHashMap<>());
    }
    return result;
  }

  private @NotNull ResolveResult resolveModule(@NotNull RuntimeModuleId moduleId, @NotNull Map<RuntimeModuleId, RuntimeModuleDescriptorImpl> dependencyPath) {
    ResolveResult cached = myResolveResults.get(moduleId);
    if (cached != null) return cached;

    RawRuntimeModuleRepositoryData rawData = getMainData();
    RawRuntimeModuleDescriptor rawDescriptor = rawData.findDescriptor(moduleId.getStringId());
    if (rawDescriptor == null) {
      if (myAdditionalData != null) {
        for (RawRuntimeModuleRepositoryData data : myAdditionalData) {
          rawDescriptor = data.findDescriptor(moduleId.getStringId());
          if (rawDescriptor != null) {
            rawData = data;
            break;
          }
        }
      }
      if (rawDescriptor == null) {
        List<RuntimeModuleId> failedPath = new ArrayList<>(dependencyPath.size() + 1);
        failedPath.addAll(dependencyPath.keySet());
        failedPath.add(moduleId);
        return new FailedResolveResult(failedPath);
      }
    }
    
    List<String> rawDependencies = rawDescriptor.getDependencies();
    List<RuntimeModuleDescriptor> resolvedDependencies = new ArrayList<>(rawDependencies.size());
    RuntimeModuleDescriptorImpl descriptor = new RuntimeModuleDescriptorImpl(moduleId, rawData.getBasePath(), rawDescriptor.getResourcePaths(), resolvedDependencies);
    dependencyPath.put(moduleId, descriptor);
    for (String dependency : rawDependencies) {
      RuntimeModuleId dependencyId = myInternedModuleIds.computeIfAbsent(dependency, RuntimeModuleId::raw);
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
      message = "Cannot find module '" + failedDependencyPath.get(0).getStringId() + "'";
    }
    else {
      List<RuntimeModuleId> reversed = new ArrayList<>(failedDependencyPath.subList(0, failedDependencyPath.size() - 1));
      Collections.reverse(reversed);
      message = "Cannot resolve module '" + moduleId.getStringId() + "': module '" + failedDependencyPath.get(failedDependencyPath.size() - 1).getStringId() + "' (" +
                reversed.stream().map(id -> " <- '" + id.getStringId() + "'").collect(Collectors.joining()).trim() + ") is not found";
    }
    throw new MalformedRepositoryException(message);
  }

  @Override
  public @NotNull List<Path> getModuleResourcePaths(@NotNull RuntimeModuleId moduleId) {
    RawRuntimeModuleRepositoryData rawData = getMainData();
    RawRuntimeModuleDescriptor rawDescriptor = rawData.findDescriptor(moduleId.getStringId());
    if (rawDescriptor == null) {
      if (myAdditionalData != null) {
        for (RawRuntimeModuleRepositoryData repository : myAdditionalData) {
          rawDescriptor = repository.findDescriptor(moduleId.getStringId());
          if (rawDescriptor != null) {
            rawData = repository;
            break;
          }
        }
      }
      if (rawDescriptor == null) {
        throw new MalformedRepositoryException("Cannot find module '" + moduleId.getStringId() + "'");
      }
    }
    //todo improve this to reuse the computed paths if the module is resolved later
    return new RuntimeModuleDescriptorImpl(moduleId, rawData.getBasePath(), rawDescriptor.getResourcePaths(), Collections.emptyList()).getResourceRootPaths();
  }

  @Override
  public @NotNull List<@NotNull Path> getBootstrapClasspath(@NotNull String bootstrapModuleName) {
    if (myMainData == null) {
      try {
        String[] bootstrapClasspath = JarFileSerializer.loadBootstrapClasspath(myDescriptorsJarPath, bootstrapModuleName);
        if (bootstrapClasspath != null) {
          List<Path> result = new ArrayList<>(bootstrapClasspath.length);
          for (String relativePath : bootstrapClasspath) {
            result.add(myDescriptorsJarPath.getParent().resolve(relativePath));
          }
          return result;
        }
      }
      catch (IOException ignore) {
      }
    }
    return getModule(RuntimeModuleId.module(bootstrapModuleName)).getModuleClasspath();
  }

  /**
   * Includes descriptors from {@code repositories} to this instance.
   * This is an internal function, it's supposed to be used by the platform only.
   */
  public void loadAdditionalRepositories(@NotNull List<@NotNull RawRuntimeModuleRepositoryData> repositories) {
    if (repositories.isEmpty()) return;
    
    if (myAdditionalData != null) {
      throw new IllegalStateException("additional repositories may be loaded only once");
    }
    myAdditionalData = repositories;
  }

  @Override
  public String toString() {
    return "RuntimeModuleRepository{descriptorsJarPath=" + myDescriptorsJarPath + '}';
  }

  private RawRuntimeModuleRepositoryData getMainData() {
    if (myMainData == null) {
      myMainData = RuntimeModuleRepositorySerialization.loadFromJar(myDescriptorsJarPath);
    }
    return myMainData;
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
