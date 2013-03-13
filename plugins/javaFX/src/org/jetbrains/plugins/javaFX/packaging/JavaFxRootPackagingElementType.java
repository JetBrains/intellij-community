/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.packaging;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.CompositePackagingElementType;
import com.intellij.packaging.impl.elements.PackagingElementFactoryImpl;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * User: anna
 * Date: 3/13/13
 */
class JavaFxRootPackagingElementType extends CompositePackagingElementType<JavaFxRootPackagingElement> {
  public static final JavaFxRootPackagingElementType JAVAFX_ROOT_ELEMENT_TYPE = new JavaFxRootPackagingElementType();

  public JavaFxRootPackagingElementType() {
    super("javafx-root", "JavaFX Archive");
  }

  @Nullable
  @Override
  public CompositePackagingElement<?> createComposite(CompositePackagingElement<?> parent,
                                                      @Nullable String baseName,
                                                      @NotNull ArtifactEditorContext context) {
    final String initialValue = PackagingElementFactoryImpl.suggestFileName(parent, baseName != null ? baseName : "archive", ".jar");
    final String path = Messages
      .showInputDialog(context.getProject(), "Enter archive name: ", "New Archive", null, initialValue, null);
    if (path == null) return null;
    return PackagingElementFactoryImpl.createDirectoryOrArchiveWithParents(path, true);
  }

  @NotNull
  @Override
  public JavaFxRootPackagingElement createEmpty(@NotNull Project project) {
    return new JavaFxRootPackagingElement();
  }

  @Override
  public Icon getCreateElementIcon() {
    return PlatformIcons.JAR_ICON;
  }
}
