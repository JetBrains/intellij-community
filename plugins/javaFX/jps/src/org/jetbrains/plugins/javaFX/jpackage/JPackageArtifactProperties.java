// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.jpackage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.ex.JpsElementBase;

/**
 * @author Bas Leijdekkers
 */
public class JPackageArtifactProperties extends JpsElementBase<JPackageArtifactProperties> {

  public String version;
  public String copyright;
  public String description;
  public String vendor;
  public boolean verbose;
  public String mainClass;

  public JPackageArtifactProperties() {
  }

  public JPackageArtifactProperties(JPackageArtifactProperties copy) {
    copyState(copy);
  }

  private void copyState(JPackageArtifactProperties copy) {
    version = copy.version;
    copyright = copy.copyright;
    description = copy.description;
    vendor = copy.vendor;
    verbose = copy.verbose;
    mainClass = copy.mainClass;
  }

  @Override
  public @NotNull JPackageArtifactProperties createCopy() {
    return new JPackageArtifactProperties(this);
  }
}