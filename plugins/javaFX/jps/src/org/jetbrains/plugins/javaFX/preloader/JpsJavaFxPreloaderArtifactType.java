// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.preloader;

import org.jetbrains.jps.model.artifact.JpsArtifactType;
import org.jetbrains.jps.model.ex.JpsElementTypeBase;

public class JpsJavaFxPreloaderArtifactType extends JpsElementTypeBase<JpsJavaFxPreloaderArtifactProperties> implements JpsArtifactType<JpsJavaFxPreloaderArtifactProperties> {
  public static final JpsJavaFxPreloaderArtifactType INSTANCE = new JpsJavaFxPreloaderArtifactType();
}
