package org.jetbrains.plugins.javaFX;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.jps.model.serialization.artifact.ArtifactPropertiesState;
import org.jetbrains.jps.model.serialization.artifact.JpsArtifactPropertiesSerializer;

import java.util.List;

/**
 * User: anna
 * Date: 3/13/13
 */
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

  @Override
  public void saveProperties(JpsJavaFxArtifactProperties properties, List<ArtifactPropertiesState> stateList) {
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
      if ("javafx-properties".equals(state.getId())) {
        return state;
      }
    }
    return null;
  }
}
