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

import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.artifacts.ArtifactPropertiesProvider;
import com.intellij.packaging.artifacts.ArtifactType;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 3/12/13
 */
public class JavaFxArtifactPropertiesProvider extends ArtifactPropertiesProvider {
  protected JavaFxArtifactPropertiesProvider() {
    super("javafx-properties");
  }

  @Override
  public boolean isAvailableFor(@NotNull ArtifactType type) {
    return type instanceof JavaFxApplicationArtifactType;
  }

  @NotNull
  @Override
  public ArtifactProperties<?> createProperties(@NotNull ArtifactType artifactType) {
    return new JavaFxArtifactProperties();
  }
  
  public static JavaFxArtifactPropertiesProvider getInstance() {
    return EP_NAME.findExtension(JavaFxArtifactPropertiesProvider.class);
  }
}
