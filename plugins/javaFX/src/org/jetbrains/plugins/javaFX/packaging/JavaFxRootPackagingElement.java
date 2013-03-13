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

import com.intellij.compiler.ant.Generator;
import com.intellij.openapi.util.Comparing;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.*;
import com.intellij.packaging.impl.elements.CompositeElementWithManifest;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
* User: anna
* Date: 3/13/13
*/
public class JavaFxRootPackagingElement extends CompositeElementWithManifest<JavaFxRootPackagingElement> {

  private String myArtifactFile;

  public JavaFxRootPackagingElement(String artifactName) {
    super(JavaFxRootPackagingElementType.JAVAFX_ROOT_ELEMENT_TYPE);
    myArtifactFile = artifactName + ".jar";
  }

  public JavaFxRootPackagingElement() {
    super(JavaFxRootPackagingElementType.JAVAFX_ROOT_ELEMENT_TYPE);
  }

  @Override
  public PackagingElementPresentation createPresentation(@NotNull ArtifactEditorContext context) {
    return new JavaFxPackagingElementPresentation(myArtifactFile);
  }

  @Override
  public List<? extends Generator> computeAntInstructions(@NotNull PackagingElementResolvingContext resolvingContext,
                                                          @NotNull AntCopyInstructionCreator creator,
                                                          @NotNull ArtifactAntGenerationContext generationContext,
                                                          @NotNull ArtifactType artifactType) {
    return Collections.emptyList();
  }

  @Override
  public void computeIncrementalCompilerInstructions(@NotNull IncrementalCompilerInstructionCreator creator,
                                                     @NotNull PackagingElementResolvingContext resolvingContext,
                                                     @NotNull ArtifactIncrementalCompilerContext compilerContext,
                                                     @NotNull ArtifactType artifactType) {
    computeChildrenInstructions(creator.archive(myArtifactFile), resolvingContext, compilerContext, artifactType);
  }

  @Override
  public boolean isEqualTo(@NotNull PackagingElement<?> element) {
    return element instanceof JavaFxRootPackagingElement && Comparing.strEqual(myArtifactFile, ((JavaFxRootPackagingElement)element).getName());
  }

  public void loadState(JavaFxRootPackagingElement state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Attribute("name")
  public String getArtifactFile() {
    return myArtifactFile;
  }

  public void setArtifactFile(String artifactFile) {
    myArtifactFile = artifactFile;
  }

  @Override
  public JavaFxRootPackagingElement getState() {
    return this;
  }

  @Override
  public String getName() {
    return myArtifactFile;
  }

  @Override
  public void rename(@NotNull String newName) {
    myArtifactFile = newName;
  }
}
