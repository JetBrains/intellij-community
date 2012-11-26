package org.jetbrains.jps.android.model.impl;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.android.model.AndroidApplicationArtifactType;
import org.jetbrains.jps.android.model.JpsAndroidApplicationArtifactProperties;
import org.jetbrains.jps.model.serialization.artifact.ArtifactPropertiesState;
import org.jetbrains.jps.model.serialization.artifact.JpsArtifactPropertiesSerializer;

import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class JpsAndroidApplicationArtifactPropertiesSerializer
  extends JpsArtifactPropertiesSerializer<JpsAndroidApplicationArtifactProperties> {

  public JpsAndroidApplicationArtifactPropertiesSerializer() {
    super("apk", AndroidApplicationArtifactType.INSTANCE);
  }

  @Override
  @NotNull
  public JpsAndroidApplicationArtifactProperties loadProperties(List<ArtifactPropertiesState> stateList) {
    final JpsAndroidApplicationArtifactProperties properties = doLoadProperties(stateList);
    return properties != null
           ? properties
           : new JpsAndroidApplicationArtifactPropertiesImpl();
  }

  private static JpsAndroidApplicationArtifactProperties doLoadProperties(List<ArtifactPropertiesState> stateList) {
    final ArtifactPropertiesState state = findAndroidApplicationProperties(stateList);

    if (state == null) {
      return null;
    }
    final Element options = state.getOptions();

    if (options == null) {
      return null;
    }
    return XmlSerializer.deserialize(options, JpsAndroidApplicationArtifactPropertiesImpl.class);
  }

  @Override
  public void saveProperties(JpsAndroidApplicationArtifactProperties properties, List<ArtifactPropertiesState> stateList) {
    final ArtifactPropertiesState state = findAndroidApplicationProperties(stateList);

    if (state == null) {
      return;
    }
    final Element element = XmlSerializer.serialize(properties);

    if (element == null) {
      return;
    }
    state.setOptions(element);
  }

  private static ArtifactPropertiesState findAndroidApplicationProperties(List<ArtifactPropertiesState> stateList) {
    for (ArtifactPropertiesState state : stateList) {
      if ("android-properties".equals(state.getId())) {
        return state;
      }
    }
    return null;
  }
}
