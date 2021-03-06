package org.jetbrains.jps.ant.model.artifacts;

import com.intellij.lang.ant.config.impl.BuildFileProperty;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.jps.model.JpsElement;

import java.util.List;

public interface JpsAntArtifactExtension extends JpsElement {
  boolean isEnabled();

  @NlsSafe String getFileUrl();

  @NlsSafe String getTargetName();

  List<BuildFileProperty> getAntProperties();
}
