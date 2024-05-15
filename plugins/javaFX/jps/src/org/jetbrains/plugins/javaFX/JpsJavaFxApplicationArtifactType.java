// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX;

import org.jetbrains.jps.model.artifact.JpsArtifactType;
import org.jetbrains.jps.model.ex.JpsElementTypeBase;

public class JpsJavaFxApplicationArtifactType extends JpsElementTypeBase<JpsJavaFxArtifactProperties> implements JpsArtifactType<JpsJavaFxArtifactProperties> {
  public static final JpsJavaFxApplicationArtifactType INSTANCE = new JpsJavaFxApplicationArtifactType();
}
