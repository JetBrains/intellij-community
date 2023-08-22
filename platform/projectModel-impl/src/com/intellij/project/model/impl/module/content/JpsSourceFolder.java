/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
  @NotNull
  private JpsModuleSourceRoot mySourceRoot;

  public JpsSourceFolder(@NotNull JpsModuleSourceRoot sourceRoot, JpsContentEntry contentEntry) {
    super(sourceRoot.getUrl(), contentEntry);
    mySourceRoot = sourceRoot;
  }

  @NotNull
  public JpsModuleSourceRoot getSourceRoot() {
    return mySourceRoot;
  }

  @Override
  public boolean isTestSource() {
    return mySourceRoot.getRootType().isForTests();
  }

  @NotNull
  @Override
  public String getPackagePrefix() {
    final @Nullable JavaSourceRootProperties properties = getJavaProperties();
    return properties != null ? properties.getPackagePrefix() : "";
  }

  @Nullable
  private JavaSourceRootProperties getJavaProperties() {
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

  @NotNull
  @Override
  public JpsModuleSourceRootType<?> getRootType() {
    return mySourceRoot.getRootType();
  }

  @NotNull
  @Override
  public JpsModuleSourceRoot getJpsElement() {
    return mySourceRoot;
  }

  @Override
  public <P extends JpsElement> void changeType(JpsModuleSourceRootType<P> newType, P properties) {
    mySourceRoot = JpsElementFactory.getInstance().createModuleSourceRoot(mySourceRoot.getUrl(), newType, properties);
  }
}
