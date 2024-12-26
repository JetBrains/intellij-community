// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.gradle.model.impl.artifacts;

import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.gradle.model.artifacts.JpsGradleArtifactExtension;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;

/**
 * @author Vladislav.Soroka
 */
public class JpsGradleArtifactExtensionImpl extends JpsCompositeElementBase<JpsGradleArtifactExtensionImpl>
  implements JpsGradleArtifactExtension {

  public static final JpsElementChildRole<JpsGradleArtifactExtension> ROLE =
    JpsElementChildRoleBase.create("gradle-properties");
  private final GradleArtifactExtensionProperties myProperties;

  public JpsGradleArtifactExtensionImpl(GradleArtifactExtensionProperties properties) {
    myProperties = properties;
  }

  private JpsGradleArtifactExtensionImpl(JpsGradleArtifactExtensionImpl original) {
    super(original);
    myProperties = XmlSerializerUtil.createCopy(original.myProperties);
  }

  @Override
  public @NotNull JpsGradleArtifactExtensionImpl createCopy() {
    return new JpsGradleArtifactExtensionImpl(this);
  }

  @Override
  public GradleArtifactExtensionProperties getProperties() {
    return myProperties;
  }

  private JpsArtifact getArtifact() {
    return (JpsArtifact)myParent;
  }

}
