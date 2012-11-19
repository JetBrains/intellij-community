package org.jetbrains.jps.android.model;

import org.jetbrains.jps.model.artifact.JpsArtifactType;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidApplicationArtifactType extends JpsArtifactType<JpsAndroidApplicationArtifactProperties> {
  public static final AndroidApplicationArtifactType INSTANCE = new AndroidApplicationArtifactType();
}
