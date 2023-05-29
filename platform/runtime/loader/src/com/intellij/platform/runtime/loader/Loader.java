// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.loader;

import com.intellij.platform.runtime.repository.*;
import com.intellij.platform.runtime.repository.serialization.RuntimeModuleRepositorySerialization;
import com.intellij.util.lang.PathClassLoader;
import com.intellij.util.lang.UrlClassLoader;
import org.jetbrains.annotations.Contract;

import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Initiates loading of a product based on IntelliJ platform. It loads information about the product modules from {@link RuntimeModuleRepository}
 * and {@link ProductModules}, and configures the classloader accordingly.
 */
public final class Loader {
  private static final String RUNTIME_REPOSITORY_PATH_PROPERTY = "intellij.platform.runtime.repository.path";
  private static final String PLATFORM_ROOT_MODULE_PROPERTY = "intellij.platform.root.module";

  public static void main(String[] args) throws Throwable {
    String repositoryPathString = System.getProperty(RUNTIME_REPOSITORY_PATH_PROPERTY);
    if (repositoryPathString == null) {
      reportError(RUNTIME_REPOSITORY_PATH_PROPERTY + " is not specified");
    }
    
    String rootModuleName = System.getProperty(PLATFORM_ROOT_MODULE_PROPERTY);
    if (rootModuleName == null) {
      reportError(PLATFORM_ROOT_MODULE_PROPERTY + " is not specified");
    }
    
    RuntimeModuleRepository repository = RuntimeModuleRepository.create(Path.of(repositoryPathString));
    RuntimeModuleDescriptor rootModule = repository.getModule(RuntimeModuleId.module(rootModuleName));
    String productModulesPath = "META-INF/" + rootModuleName + "/product-modules.xml";
    InputStream moduleGroupStream = rootModule.readFile(productModulesPath);
    if (moduleGroupStream == null) {
      reportError(productModulesPath + " is not found in " + rootModuleName + " module");
    }
    ProductModules productModules = RuntimeModuleRepositorySerialization.loadProductModules(moduleGroupStream, productModulesPath, repository);
    String bootstrapModuleName = System.getProperty("intellij.platform.bootstrap.module", "intellij.platform.bootstrap");
    Set<Path> classpath = new LinkedHashSet<>(repository.getModule(RuntimeModuleId.module(bootstrapModuleName)).getModuleClasspath());
    for (IncludedRuntimeModule item : productModules.getMainModuleGroup().getIncludedModules()) {
      classpath.addAll(item.getModuleDescriptor().getResourceRootPaths());
    }
    PathClassLoader classLoader = new PathClassLoader(UrlClassLoader.build().files(new ArrayList<>(classpath)).parent(Loader.class.getClassLoader()));

    String bootstrapClassName = System.getProperty("intellij.platform.bootstrap.class.name", "com.intellij.platform.bootstrap.ModularMain");
    Class<?> bootstrapClass = Class.forName(bootstrapClassName, true, classLoader);
    MethodHandles.publicLookup()
      .findStatic(bootstrapClass, "main", MethodType.methodType(void.class, RuntimeModuleRepository.class, ProductModules.class, String[].class))
      .invokeExact(repository, productModules, args);
  }

  @Contract("_ -> fail")
  private static void reportError(String message) {
    //todo reuse code from StartupErrorReporter
    //noinspection UseOfSystemOutOrSystemErr
    System.err.println(message);
    System.exit(3);
  }
}
