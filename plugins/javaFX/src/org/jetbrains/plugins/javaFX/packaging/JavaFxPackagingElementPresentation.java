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

import com.intellij.ide.projectView.PresentationData;
import com.intellij.packaging.ui.PackagingElementPresentation;
import com.intellij.packaging.ui.PackagingElementWeights;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 3/13/13
 */
class JavaFxPackagingElementPresentation extends PackagingElementPresentation {
  private String myArtifactFile;

  public JavaFxPackagingElementPresentation(String artifactFile) {
    this.myArtifactFile = artifactFile;
  }

  @Override
  public String getPresentableName() {
    return myArtifactFile;
  }

  public void render(@NotNull PresentationData presentationData,
                     SimpleTextAttributes mainAttributes,
                     SimpleTextAttributes commentAttributes) {
    presentationData.setIcon(PlatformIcons.JAR_ICON);
    presentationData.addText(myArtifactFile, mainAttributes);
  }

  @Override
  public int getWeight() {
    return PackagingElementWeights.FILE_COPY;
  }
}
