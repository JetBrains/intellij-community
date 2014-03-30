package org.jetbrains.plugins.javaFX;

import org.jetbrains.jps.model.artifact.JpsArtifactType;
import org.jetbrains.jps.model.ex.JpsElementTypeBase;

/**
 * User: anna
 * Date: 3/13/13
 */
public class JpsJavaFxApplicationArtifactType extends JpsElementTypeBase<JpsJavaFxArtifactProperties> implements JpsArtifactType<JpsJavaFxArtifactProperties> {
  public static final JpsJavaFxApplicationArtifactType INSTANCE = new JpsJavaFxApplicationArtifactType();
}
