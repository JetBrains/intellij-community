// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.mock.MockApplication;
import com.intellij.mock.MockProjectEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingManagerImpl;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Modifier;


public abstract class PlatformLiteFixture extends UsefulTestCase {
  protected MockProjectEx myProject;

  @NotNull
  public static MockApplication getApplication() {
    return (MockApplication)ApplicationManager.getApplication();
  }

  @NotNull
  public MockApplication initApplication() {
    MockApplication app = new MockApplication(getTestRootDisposable());
    ApplicationManager.setApplication(app,
                                      () -> FileTypeManager.getInstance(),
                                      getTestRootDisposable());
    app.registerService(EncodingManager.class, EncodingManagerImpl.class);
    return app;
  }

  @Override
  protected void tearDown() throws Exception {
    myProject = null;
    super.tearDown();
  }

  protected <T> void registerExtension(@NotNull ExtensionPointName<T> extensionPointName, @NotNull T extension) {
    registerExtension(Extensions.getRootArea(), extensionPointName, extension);
  }

  public <T> void registerExtension(@NotNull ExtensionsArea area, @NotNull ExtensionPointName<T> name, @NotNull T extension) {
    //noinspection unchecked
    registerExtensionPoint(area, name, (Class<T>)extension.getClass());
    area.<T>getExtensionPoint(name.getName()).registerExtension(extension, getTestRootDisposable());
  }

  protected <T> void registerExtensionPoint(@NotNull ExtensionPointName<T> extensionPointName, @NotNull Class<T> aClass) {
    registerExtensionPoint(Extensions.getRootArea(), extensionPointName, aClass);
  }

  private <T> void registerExtensionPoint(@NotNull ExtensionsArea area,
                                          @NotNull ExtensionPointName<T> extensionPointName,
                                          @NotNull Class<? extends T> aClass) {
    if (!area.hasExtensionPoint(extensionPointName)) {
      ExtensionPoint.Kind kind = aClass.isInterface() || (aClass.getModifiers() & Modifier.ABSTRACT) != 0
                                 ? ExtensionPoint.Kind.INTERFACE
                                 : ExtensionPoint.Kind.BEAN_CLASS;
      ((ExtensionsAreaImpl)area).registerExtensionPoint(extensionPointName, aClass.getName(), kind, getTestRootDisposable());
    }
  }
}
