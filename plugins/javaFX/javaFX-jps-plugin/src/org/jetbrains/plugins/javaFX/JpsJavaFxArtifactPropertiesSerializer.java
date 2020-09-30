// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.jps.model.serialization.artifact.ArtifactPropertiesState;
import org.jetbrains.jps.model.serialization.artifact.JpsArtifactPropertiesSerializer;

import java.util.List;

public class JpsJavaFxArtifactPropertiesSerializer extends JpsArtifactPropertiesSerializer<JpsJavaFxArtifactProperties> {
  public JpsJavaFxArtifactPropertiesSerializer() {
    super("javafx", JpsJavaFxApplicationArtifactType.INSTANCE);
  }

  @Override
  public JpsJavaFxArtifactProperties loadProperties(List<ArtifactPropertiesState> stateList) {
    final JpsJavaFxArtifactProperties.MyState properties = doLoadProperties(stateList);
    return properties != null ? new JpsJavaFxArtifactProperties(properties) : new JpsJavaFxArtifactProperties();
  }

  private static JpsJavaFxArtifactProperties.MyState doLoadProperties(List<ArtifactPropertiesState> stateList) {
    final ArtifactPropertiesState state = findApplicationProperties(stateList);

    if (state == null) {
      return null;
    }
    final Element options = state.getOptions();

    if (options == null) {
      return null;
    }
    return XmlSerializer.deserialize(options, JpsJavaFxArtifactProperties.MyState.class);
  }

  private static ArtifactPropertiesState findApplicationProperties(List<ArtifactPropertiesState> stateList) {
    for (ArtifactPropertiesState state : stateList) {
      if ("javafx-properties".equals(state.getId())) {
        return state;
      }
    }
    return null;
  }
}
