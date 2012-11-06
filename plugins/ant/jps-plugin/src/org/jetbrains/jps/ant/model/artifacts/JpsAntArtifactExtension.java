package org.jetbrains.jps.ant.model.artifacts;

import com.intellij.lang.ant.config.impl.BuildFileProperty;
import org.jetbrains.jps.model.JpsElement;

import java.util.List;

/**
 * @author nik
 */
public interface JpsAntArtifactExtension extends JpsElement {
  boolean isEnabled();

  String getFileUrl();

  String getTargetName();

  List<BuildFileProperty> getAntProperties();
}
