// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.mock.MockApplicationEx;
import com.intellij.mock.MockProjectEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingManagerImpl;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.MutablePicoContainer;

import java.lang.reflect.Modifier;

/**
 * @author yole
 */
public abstract class PlatformLiteFixture extends UsefulTestCase {
  protected MockProjectEx myProject;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Extensions.cleanRootArea(getTestRootDisposable());
  }

  public static MockApplicationEx getApplication() {
    return (MockApplicationEx)ApplicationManager.getApplication();
  }

  public void initApplication() {
    //if (ApplicationManager.getApplication() instanceof MockApplicationEx) return;
    final MockApplicationEx instance = new MockApplicationEx(getTestRootDisposable());
    ApplicationManager.setApplication(instance,
                                      () -> FileTypeManager.getInstance(),
                                      getTestRootDisposable());
    getApplication().registerService(EncodingManager.class, EncodingManagerImpl.class);
  }

  @Override
  protected void tearDown() throws Exception {
    myProject = null;
    try {
      super.tearDown();
    }
    finally {
      clearFields(this);
    }
  }

  protected <T> void registerExtension(@NotNull ExtensionPointName<T> extensionPointName, @NotNull T t) {
    registerExtension(Extensions.getRootArea(), extensionPointName, t);
  }

  public <T> void registerExtension(@NotNull ExtensionsArea area, @NotNull ExtensionPointName<T> name, @NotNull T t) {
    //noinspection unchecked
    registerExtensionPoint(area, name, (Class<T>)t.getClass());
    PlatformTestUtil.registerExtension(area, name, t, getTestRootDisposable());
  }

  protected <T> void registerExtensionPoint(@NotNull ExtensionPointName<T> extensionPointName, @NotNull Class<T> aClass) {
    registerExtensionPoint(Extensions.getRootArea(), extensionPointName, aClass);
  }

  protected <T> void registerExtensionPoint(@NotNull ExtensionsArea area,
                                            @NotNull ExtensionPointName<T> extensionPointName,
                                            @NotNull Class<? extends T> aClass) {
    if (!area.hasExtensionPoint(extensionPointName)) {
      ExtensionPoint.Kind kind = aClass.isInterface() || (aClass.getModifiers() & Modifier.ABSTRACT) != 0 ? ExtensionPoint.Kind.INTERFACE : ExtensionPoint.Kind.BEAN_CLASS;
      area.registerExtensionPoint(extensionPointName, aClass.getName(), kind, getTestRootDisposable());
    }
  }

  protected <T> void registerApplicationService(Class<T> aClass, T object) {
    getApplication().registerService(aClass, object);
    Disposer.register(getTestRootDisposable(), () -> getApplication().getPicoContainer().unregisterComponent(aClass.getName()));
  }

  protected void registerComponentImplementation(final MutablePicoContainer container, final Class<?> key, final Class<?> implementation) {
    container.unregisterComponent(key);
    container.registerComponentImplementation(key, implementation);
  }

  public static <T> T registerComponentInstance(final MutablePicoContainer container, final Class<T> key, final T implementation) {
    Object old = container.getComponentInstance(key);
    container.unregisterComponent(key);
    container.registerComponentInstance(key, implementation);
    //noinspection unchecked
    return (T)old;
  }

  public static <T> T registerComponentInstance(final ComponentManager container, final Class<T> key, final T implementation) {
    return registerComponentInstance((MutablePicoContainer)container.getPicoContainer(), key, implementation);
  }

}
