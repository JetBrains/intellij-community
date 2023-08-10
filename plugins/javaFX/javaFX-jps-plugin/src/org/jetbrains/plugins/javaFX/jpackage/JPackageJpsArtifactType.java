// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
