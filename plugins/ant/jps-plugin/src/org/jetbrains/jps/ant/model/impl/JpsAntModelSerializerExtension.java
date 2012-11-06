/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.ant.model.impl;

import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.ant.model.artifacts.JpsAntArtifactExtension;
import org.jetbrains.jps.ant.model.impl.artifacts.AntArtifactExtensionProperties;
import org.jetbrains.jps.ant.model.impl.artifacts.JpsAntArtifactExtensionImpl;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.artifact.JpsArtifactExtensionSerializer;

import java.util.Arrays;
import java.util.List;

/**
 * @author nik
 */
public class JpsAntModelSerializerExtension extends JpsModelSerializerExtension {
  @Override
  public List<? extends JpsArtifactExtensionSerializer<?>> getArtifactExtensionSerializers() {
    return Arrays.asList(new JpsAntArtifactExtensionSerializer("ant-postprocessing", JpsAntArtifactExtensionImpl.POSTPROCESSING_ROLE),
                         new JpsAntArtifactExtensionSerializer("ant-preprocessing", JpsAntArtifactExtensionImpl.PREPROCESSING_ROLE));
  }

  private static class JpsAntArtifactExtensionSerializer extends JpsArtifactExtensionSerializer<JpsAntArtifactExtension> {
    private JpsAntArtifactExtensionSerializer(final String id, final JpsElementChildRole<JpsAntArtifactExtension> role) {
      super(id, role);
    }

    @Override
    public JpsAntArtifactExtension loadExtension(@Nullable Element optionsTag) {
      AntArtifactExtensionProperties properties = optionsTag != null ? XmlSerializer.deserialize(optionsTag, AntArtifactExtensionProperties.class) : null;
      return new JpsAntArtifactExtensionImpl(properties != null ? properties : null);
    }

    @Override
    public void saveExtension(@NotNull JpsAntArtifactExtension extension, @NotNull Element optionsTag) {
      AntArtifactExtensionProperties properties = ((JpsAntArtifactExtensionImpl)extension).getProperties();
      XmlSerializer.serializeInto(properties, optionsTag, new SkipDefaultValuesSerializationFilters());
    }
  }
}
