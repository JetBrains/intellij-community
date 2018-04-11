package com.intellij.remoteServer.agent.impl;

import org.jetbrains.annotations.Nullable;

/**
 * @author michael.golubev
 */
public class CallerClassLoaderProvider {

  private final ClassLoader myCallerClassLoader;

  public CallerClassLoaderProvider(@Nullable ClassLoader callerClassLoader) {
    myCallerClassLoader = callerClassLoader;
  }

  public ClassLoader getCallerClassLoader(Class<?> classOfDefaultLoader) {
    return myCallerClassLoader == null ? classOfDefaultLoader.getClassLoader() : myCallerClassLoader;
  }
}
