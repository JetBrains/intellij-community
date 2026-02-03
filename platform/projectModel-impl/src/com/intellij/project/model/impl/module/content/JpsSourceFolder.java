// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.project.model.impl.module.content;

import com.intellij.openapi.roots.SourceFolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.java.JavaSourceRootProperties;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRoot;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

public class JpsSourceFolder extends JpsContentFolderBase implements SourceFolder {
  private @NotNull JpsModuleSourceRoot mySourceRoot;

  public JpsSourceFolder(@NotNull JpsModuleSourceRoot sourceRoot, JpsContentEntry contentEntry) {
    super(sourceRoot.getUrl(), contentEntry);
    mySourceRoot = sourceRoot;
  }

  public @NotNull JpsModuleSourceRoot getSourceRoot() {
    return mySourceRoot;
  }

  @Override
  public boolean isTestSource() {
    return mySourceRoot.getRootType().isForTests();
  }

  @Override
  public @NotNull String getPackagePrefix() {
    final @Nullable JavaSourceRootProperties properties = getJavaProperties();
    return properties != null ? properties.getPackagePrefix() : "";
  }

  private @Nullable JavaSourceRootProperties getJavaProperties() {
    if (mySourceRoot.getRootType() == JavaSourceRootType.SOURCE) {
      return mySourceRoot.getProperties(JavaSourceRootType.SOURCE);
    }
    if (mySourceRoot.getRootType() == JavaSourceRootType.TEST_SOURCE) {
      return mySourceRoot.getProperties(JavaSourceRootType.TEST_SOURCE);
    }
    return null;
  }

  @Override
  public void setPackagePrefix(@NotNull String packagePrefix) {
    JavaSourceRootProperties properties = getJavaProperties();
    if (properties != null) {
      properties.setPackagePrefix(packagePrefix);
    }
  }

  @Override
  public @NotNull JpsModuleSourceRootType<?> getRootType() {
    return mySourceRoot.getRootType();
  }

  @Override
  public @NotNull JpsModuleSourceRoot getJpsElement() {
    return mySourceRoot;
  }

  @Override
  public <P extends JpsElement> void changeType(JpsModuleSourceRootType<P> newType, P properties) {
    mySourceRoot = JpsElementFactory.getInstance().createModuleSourceRoot(mySourceRoot.getUrl(), newType, properties);
  }
}
