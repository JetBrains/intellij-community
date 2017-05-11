package org.jetbrains.plugins.javaFX;

import org.jetbrains.jps.model.artifact.JpsArtifactType;
import org.jetbrains.jps.model.ex.JpsElementTypeBase;

public class JpsJavaFxApplicationArtifactType extends JpsElementTypeBase<JpsJavaFxArtifactProperties> implements JpsArtifactType<JpsJavaFxArtifactProperties> {
  public static final JpsJavaFxApplicationArtifactType INSTANCE = new JpsJavaFxApplicationArtifactType();
}
