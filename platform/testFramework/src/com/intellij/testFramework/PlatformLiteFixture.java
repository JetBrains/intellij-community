/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.Getter;
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
                                      new Getter<FileTypeRegistry>() {
                                        @Override
                                        public FileTypeRegistry get() {
                                          return FileTypeManager.getInstance();
                                        }
                                      },
                                      getTestRootDisposable());
    getApplication().registerService(EncodingManager.class, EncodingManagerImpl.class);
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    clearFields(this);
    myProject = null;
  }

  protected <T> void registerExtension(final ExtensionPointName<T> extensionPointName, @NotNull final T t) {
    registerExtension(Extensions.getRootArea(), extensionPointName, t);
  }

  public <T> void registerExtension(final ExtensionsArea area, final ExtensionPointName<T> name, final T t) {
    registerExtensionPoint(area, name, (Class<T>)t.getClass());


    PlatformTestUtil.registerExtension(area, name, t, myTestRootDisposable);
  }

  protected <T> void registerExtensionPoint(final ExtensionPointName<T> extensionPointName, final Class<T> aClass) {
    registerExtensionPoint(Extensions.getRootArea(), extensionPointName, aClass);
  }

  protected <T> void registerExtensionPoint(final ExtensionsArea area, final ExtensionPointName<T> extensionPointName,
                                            final Class<? extends T> aClass) {
    final String name = extensionPointName.getName();
    if (!area.hasExtensionPoint(name)) {
      ExtensionPoint.Kind kind = aClass.isInterface() || (aClass.getModifiers() & Modifier.ABSTRACT) != 0 ? ExtensionPoint.Kind.INTERFACE : ExtensionPoint.Kind.BEAN_CLASS;
      area.registerExtensionPoint(name, aClass.getName(), kind);
    }
  }

  protected void registerComponentImplementation(final MutablePicoContainer container, final Class<?> key, final Class<?> implementation) {
    container.unregisterComponent(key);
    container.registerComponentImplementation(key, implementation);
  }

  public static <T> T registerComponentInstance(final MutablePicoContainer container, final Class<T> key, final T implementation) {
    Object old = container.getComponentInstance(key);
    container.unregisterComponent(key);
    container.registerComponentInstance(key, implementation);
    return (T)old;
  }

  public static <T> T registerComponentInstance(final ComponentManager container, final Class<T> key, final T implementation) {
    return registerComponentInstance((MutablePicoContainer)container.getPicoContainer(), key, implementation);
  }

}
