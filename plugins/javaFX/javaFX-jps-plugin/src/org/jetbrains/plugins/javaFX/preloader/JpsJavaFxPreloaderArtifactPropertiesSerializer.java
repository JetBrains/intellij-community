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
package org.jetbrains.plugins.javaFX.preloader;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.jps.model.serialization.artifact.ArtifactPropertiesState;
import org.jetbrains.jps.model.serialization.artifact.JpsArtifactPropertiesSerializer;

import java.util.List;

/**
 * User: anna
 * Date: 3/13/13
 */
public class JpsJavaFxPreloaderArtifactPropertiesSerializer extends JpsArtifactPropertiesSerializer<JpsJavaFxPreloaderArtifactProperties> {
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

  @Override
  public void saveProperties(JpsJavaFxPreloaderArtifactProperties properties, List<ArtifactPropertiesState> stateList) {
    final ArtifactPropertiesState state = findApplicationProperties(stateList);

    if (state == null) {
      return;
    }
    final Element element = XmlSerializer.serialize(properties);

    if (element == null) {
      return;
    }
    state.setOptions(element);
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
