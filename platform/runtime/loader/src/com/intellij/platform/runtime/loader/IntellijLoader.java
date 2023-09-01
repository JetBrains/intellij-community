// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.runtime.loader;

import com.intellij.platform.runtime.repository.ProductModules;
import com.intellij.platform.runtime.repository.RuntimeModuleRepository;
import com.intellij.util.lang.PathClassLoader;
import org.jetbrains.annotations.Contract;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Initiates loading of a product based on IntelliJ platform. It loads information about the product modules from {@link RuntimeModuleRepository}
 * and {@link ProductModules}, and configures the classloader accordingly.
 * <p>It's an experimental way, and it isn't used in production yet.</p>
 */
public final class IntellijLoader {
  private static final String RUNTIME_REPOSITORY_PATH_PROPERTY = "intellij.platform.runtime.repository.path";

  public static void main(String[] args) throws Throwable {
    long startTimeNano = System.nanoTime();
    long startTimeUnixNano = System.currentTimeMillis() * 1_000_000;
    ArrayList<Object> startupTimings = new ArrayList<>(16);
    startupTimings.add("startup begin");
    startupTimings.add(startTimeNano);

    String repositoryPathString = System.getProperty(RUNTIME_REPOSITORY_PATH_PROPERTY);
    if (repositoryPathString == null) {
      reportError(RUNTIME_REPOSITORY_PATH_PROPERTY + " is not specified");
    }
    
    RuntimeModuleRepository repository = RuntimeModuleRepository.create(Path.of(repositoryPathString));
    List<Path> bootstrapClasspath = repository.getBootstrapClasspath("intellij.platform.bootstrap");
    startupTimings.add("calculating bootstrap classpath");
    startupTimings.add(System.nanoTime());
    
    ClassLoader appClassLoader = IntellijLoader.class.getClassLoader();
    if (!(appClassLoader instanceof PathClassLoader)) {
      reportError("JVM for IntelliJ must be started with -Djava.system.class.loader=com.intellij.util.lang.PathClassLoader parameter");
    }
    ((PathClassLoader)appClassLoader).getClassPath().addFiles(bootstrapClasspath);

    String bootstrapClassName = "com.intellij.platform.bootstrap.ModularMain";
    Class<?> bootstrapClass = Class.forName(bootstrapClassName, true, appClassLoader);
    MethodHandle methodHandle = MethodHandles.publicLookup()
      .findStatic(bootstrapClass, "main",
                  MethodType.methodType(void.class, RuntimeModuleRepository.class, String[].class, ArrayList.class, long.class));
    startupTimings.add("obtaining main method handle");
    startupTimings.add(System.nanoTime());
    
    methodHandle.invokeExact(repository, args, startupTimings, startTimeUnixNano);
  }

  @Contract("_ -> fail")
  private static void reportError(String message) {
    //todo reuse code from StartupErrorReporter
    //noinspection UseOfSystemOutOrSystemErr
    System.err.println(message);
    System.exit(3);//com.intellij.idea.AppExitCodes.STARTUP_EXCEPTION
  }
}
