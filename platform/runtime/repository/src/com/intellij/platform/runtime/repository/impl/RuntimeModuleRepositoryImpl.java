// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.repository.impl;

import com.intellij.platform.runtime.repository.MalformedRepositoryException;
import com.intellij.platform.runtime.repository.RuntimeModuleDescriptor;
import com.intellij.platform.runtime.repository.RuntimeModuleId;
import com.intellij.platform.runtime.repository.RuntimeModuleRepository;
import com.intellij.platform.runtime.repository.serialization.RawRuntimeModuleDescriptor;
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
  private volatile Map<String, RawRuntimeModuleDescriptor> myRawDescriptors;
  private final Path myDescriptorsJarPath;
  private final Path myBasePath;
  private final Map<String, RuntimeModuleId> myInternedModuleIds;

  public RuntimeModuleRepositoryImpl(@NotNull Path descriptorsJarPath) {
    myDescriptorsJarPath = descriptorsJarPath;
    myBasePath = myDescriptorsJarPath.getParent();
    myResolveResults = new ConcurrentHashMap<>();
    myInternedModuleIds = new ConcurrentHashMap<>();
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

    RawRuntimeModuleDescriptor rawDescriptor = getRawDescriptors().get(moduleId.getStringId());
    if (rawDescriptor == null) {
      List<RuntimeModuleId> failedPath = new ArrayList<>(dependencyPath.size() + 1);
      failedPath.addAll(dependencyPath.keySet());
      failedPath.add(moduleId);
      return new FailedResolveResult(failedPath);
    }
    
    List<String> rawDependencies = rawDescriptor.getDependencies();
    List<RuntimeModuleDescriptor> resolvedDependencies = new ArrayList<>(rawDependencies.size());
    RuntimeModuleDescriptorImpl descriptor = new RuntimeModuleDescriptorImpl(moduleId, myBasePath, rawDescriptor.getResourcePaths(), resolvedDependencies);
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
  @NotNull
  public RuntimeModuleDescriptor getModule(@NotNull RuntimeModuleId moduleId) {
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
    RawRuntimeModuleDescriptor rawDescriptor = getRawDescriptors().get(moduleId.getStringId());
    if (rawDescriptor == null) {
      throw new MalformedRepositoryException("Cannot find module '" + moduleId.getStringId() + "'");
    }
    //todo improve this to reuse the computed paths if the module is resolved later
    return new RuntimeModuleDescriptorImpl(moduleId, myBasePath, rawDescriptor.getResourcePaths(), Collections.emptyList()).getResourceRootPaths();
  }

  @Override
  public @NotNull List<@NotNull Path> getBootstrapClasspath(@NotNull String bootstrapModuleName) {
    if (myRawDescriptors == null) {
      try {
        String[] bootstrapClasspath = JarFileSerializer.loadBootstrapClasspath(myDescriptorsJarPath, bootstrapModuleName);
        if (bootstrapClasspath != null) {
          List<Path> result = new ArrayList<>(bootstrapClasspath.length);
          for (String relativePath : bootstrapClasspath) {
            result.add(myBasePath.resolve(relativePath));
          }
          return result;
        }
      }
      catch (IOException ignore) {
      }
    }
    return getModule(RuntimeModuleId.module(bootstrapModuleName)).getModuleClasspath();
  }

  @Override
  public String toString() {
    return "RuntimeModuleRepository{descriptorsJarPath=" + myDescriptorsJarPath + '}';
  }

  private Map<String, RawRuntimeModuleDescriptor> getRawDescriptors() {
    if (myRawDescriptors == null) {
      myRawDescriptors = RuntimeModuleRepositorySerialization.loadFromJar(myDescriptorsJarPath);
    }
    return myRawDescriptors;
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
