package org.jetbrains.plugins.javaFX;

import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.artifact.JpsArtifactPropertiesSerializer;
import org.jetbrains.jps.model.serialization.artifact.JpsPackagingElementSerializer;

import java.util.Collections;
import java.util.List;

/**
 * User: anna
 * Date: 3/13/13
 */
public class JpsJavaFxModelSerializerExtension extends JpsModelSerializerExtension {
  @Override
  public List<? extends JpsArtifactPropertiesSerializer<?>> getArtifactTypePropertiesSerializers() {
    return Collections.singletonList(new JpsJavaFxArtifactPropertiesSerializer());
  }

  @Override
  public List<? extends JpsPackagingElementSerializer<?>> getPackagingElementSerializers() {
    return Collections.singletonList(new JpsJavaFxRootElementSerializer());
  }
}
