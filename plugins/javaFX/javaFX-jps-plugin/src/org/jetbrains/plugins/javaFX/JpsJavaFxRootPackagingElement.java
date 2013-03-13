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
package org.jetbrains.plugins.javaFX;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.artifact.impl.elements.JpsCompositePackagingElementBase;

/**
 * User: anna
 * Date: 3/13/13
 */
public class JpsJavaFxRootPackagingElement extends JpsCompositePackagingElementBase<JpsJavaFxRootPackagingElement> {
  private String myArtifactFile;

  public JpsJavaFxRootPackagingElement(String artifactFile) {
    myArtifactFile = artifactFile;
  }

  private JpsJavaFxRootPackagingElement(JpsJavaFxRootPackagingElement original) {
    super(original);
    myArtifactFile = original.myArtifactFile;
  }

  @NotNull
  @Override
  public JpsJavaFxRootPackagingElement createCopy() {
    return new JpsJavaFxRootPackagingElement(this);
  }

  public String getArtifactFile() {
    return myArtifactFile;
  }

  public void setArtifactFile(String directoryName) {
    if (!myArtifactFile.equals(directoryName)) {
      myArtifactFile = directoryName;
      fireElementChanged();
    }
  }
}

