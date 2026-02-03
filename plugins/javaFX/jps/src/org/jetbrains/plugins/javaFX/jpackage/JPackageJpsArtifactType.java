// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.jpackage;

import org.jetbrains.jps.model.artifact.JpsArtifactType;
import org.jetbrains.jps.model.ex.JpsElementTypeBase;

/**
 * @author Bas Leijdekkers
 */
public class JPackageJpsArtifactType extends JpsElementTypeBase<JPackageArtifactProperties>
  implements JpsArtifactType<JPackageArtifactProperties> {

  public static final JPackageJpsArtifactType INSTANCE = new JPackageJpsArtifactType();

  private JPackageJpsArtifactType() {}
}
