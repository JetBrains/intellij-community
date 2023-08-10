// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.jpackage;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.artifact.ArtifactPropertiesState;
import org.jetbrains.jps.model.serialization.artifact.JpsArtifactPropertiesSerializer;

import java.util.Collections;
import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class JPackageJpsModelSerializerExtension extends JpsModelSerializerExtension {

  @Override
  public @NotNull List<? extends JpsArtifactPropertiesSerializer<?>> getArtifactTypePropertiesSerializers() {
    return Collections.singletonList(new JPackageJpsArtifactPropertiesSerializer());
  }

  static class JPackageJpsArtifactPropertiesSerializer extends JpsArtifactPropertiesSerializer<JPackageArtifactProperties> {

    JPackageJpsArtifactPropertiesSerializer() {
      super("jpackage", JPackageJpsArtifactType.INSTANCE);
    }

    @Override
    public JPackageArtifactProperties loadProperties(List<ArtifactPropertiesState> stateList) {
      final ArtifactPropertiesState state = findApplicationProperties(stateList);
      if (state != null) {
        final Element options = state.getOptions();
        if (options != null) {
          return new JPackageArtifactProperties(XmlSerializer.deserialize(options, JPackageArtifactProperties.class));
        }
      }
      return new JPackageArtifactProperties();
    }

    private static ArtifactPropertiesState findApplicationProperties(List<ArtifactPropertiesState> stateList) {
      return ContainerUtil.find(stateList, state -> "jpackage-properties".equals(state.getId()));
    }
  }
}
