// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.ant.model.impl.artifacts;

import com.intellij.lang.ant.config.impl.BuildFileProperty;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.ant.model.artifacts.JpsAntArtifactExtension;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;

import java.util.ArrayList;
import java.util.List;

public final class JpsAntArtifactExtensionImpl extends JpsCompositeElementBase<JpsAntArtifactExtensionImpl> implements JpsAntArtifactExtension {
  public static final JpsElementChildRole<JpsAntArtifactExtension> PREPROCESSING_ROLE = JpsElementChildRoleBase.create("ant preprocessing");
  public static final JpsElementChildRole<JpsAntArtifactExtension> POSTPROCESSING_ROLE = JpsElementChildRoleBase.create("ant postprocessing");
  public static final @NonNls String ARTIFACT_OUTPUT_PATH_PROPERTY = "artifact.output.path";
  private final AntArtifactExtensionProperties myProperties;

  public JpsAntArtifactExtensionImpl(AntArtifactExtensionProperties properties) {
    myProperties = properties;
  }

  private JpsAntArtifactExtensionImpl(JpsAntArtifactExtensionImpl original) {
    super(original);
    myProperties = XmlSerializerUtil.createCopy(original.myProperties);
  }

  @Override
  public @NotNull JpsAntArtifactExtensionImpl createCopy() {
    return new JpsAntArtifactExtensionImpl(this);
  }

  public AntArtifactExtensionProperties getProperties() {
    return myProperties;
  }

  @Override
  public boolean isEnabled() {
    return myProperties.myEnabled;
  }

  @Override
  public String getFileUrl() {
    return myProperties.myFileUrl;
  }

  @Override
  public String getTargetName() {
    return myProperties.myTargetName;
  }

  private JpsArtifact getArtifact() {
    return (JpsArtifact)myParent;
  }

  @Override
  public List<BuildFileProperty> getAntProperties() {
    final List<BuildFileProperty> properties = new ArrayList<>();
    properties.add(new BuildFileProperty(ARTIFACT_OUTPUT_PATH_PROPERTY, getArtifact().getOutputPath()));
    properties.addAll(myProperties.myUserProperties);
    return properties;
  }
}
