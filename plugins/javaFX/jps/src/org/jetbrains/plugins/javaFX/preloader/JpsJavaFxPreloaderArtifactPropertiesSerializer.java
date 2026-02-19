// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.preloader;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.jps.model.serialization.artifact.ArtifactPropertiesState;
import org.jetbrains.jps.model.serialization.artifact.JpsArtifactPropertiesSerializer;

import java.util.List;

public final class JpsJavaFxPreloaderArtifactPropertiesSerializer extends JpsArtifactPropertiesSerializer<JpsJavaFxPreloaderArtifactProperties> {
  public JpsJavaFxPreloaderArtifactPropertiesSerializer() {
    super("javafx-preloader", JpsJavaFxPreloaderArtifactType.INSTANCE);
  }

  @Override
  public JpsJavaFxPreloaderArtifactProperties loadProperties(List<ArtifactPropertiesState> stateList) {
    final JpsJavaFxPreloaderArtifactProperties.MyState properties = doLoadProperties(stateList);
    return properties != null ? new JpsJavaFxPreloaderArtifactProperties(properties) : new JpsJavaFxPreloaderArtifactProperties();
  }

  private static JpsJavaFxPreloaderArtifactProperties.MyState doLoadProperties(List<ArtifactPropertiesState> stateList) {
    final ArtifactPropertiesState state = findApplicationProperties(stateList);

    if (state == null) {
      return null;
    }
    final Element options = state.getOptions();

    if (options == null) {
      return null;
    }
    return XmlSerializer.deserialize(options, JpsJavaFxPreloaderArtifactProperties.MyState.class);
  }

  private static ArtifactPropertiesState findApplicationProperties(List<ArtifactPropertiesState> stateList) {
    for (ArtifactPropertiesState state : stateList) {
      if ("javafx-preloader-properties".equals(state.getId())) {
        return state;
      }
    }
    return null;
  }
}
