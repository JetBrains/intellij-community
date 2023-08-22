// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.packaging.jpackage;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.packaging.artifacts.ArtifactProperties;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.ArtifactPropertiesEditor;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
class JPackageArtifactProperties extends ArtifactProperties<JPackageArtifactProperties> {

  public @NlsSafe String version;
  public @NlsSafe String copyright;
  public @NlsSafe String description;
  public @NlsSafe String vendor;
  public boolean verbose;
  public String mainClass;

  @Override
  public ArtifactPropertiesEditor createEditor(@NotNull ArtifactEditorContext context) {
    return new JPackageArtifactPropertiesEditor(this, context.getProject());
  }

  @Override
  public @Nullable JPackageArtifactProperties getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull JPackageArtifactProperties state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
