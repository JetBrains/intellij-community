// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.artifact.JpsArtifactPropertiesSerializer;

import java.util.Collections;
import java.util.List;

public final class JpsJavaFxModelSerializerExtension extends JpsModelSerializerExtension {
  @Override
  public @NotNull List<? extends JpsArtifactPropertiesSerializer<?>> getArtifactTypePropertiesSerializers() {
    return Collections.singletonList(new JpsJavaFxArtifactPropertiesSerializer());
  }
}
