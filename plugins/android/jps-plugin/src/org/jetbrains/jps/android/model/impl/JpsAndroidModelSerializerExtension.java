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
package org.jetbrains.jps.android.model.impl;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.AndroidJpsUtil;
import org.jetbrains.jps.android.model.JpsAndroidModuleExtension;
import org.jetbrains.jps.android.model.JpsAndroidSdkProperties;
import org.jetbrains.jps.android.model.JpsAndroidSdkType;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;
import org.jetbrains.jps.model.serialization.artifact.JpsArtifactPropertiesSerializer;
import org.jetbrains.jps.model.serialization.artifact.JpsPackagingElementSerializer;
import org.jetbrains.jps.model.serialization.library.JpsSdkPropertiesSerializer;
import org.jetbrains.jps.model.serialization.facet.JpsFacetConfigurationSerializer;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author nik
 */
public class JpsAndroidModelSerializerExtension extends JpsModelSerializerExtension {

  private static final List<? extends JpsFacetConfigurationSerializer<JpsAndroidModuleExtension>> FACET_PROPERTIES_LOADERS =
    Arrays.asList(new JpsFacetConfigurationSerializer<JpsAndroidModuleExtension>(JpsAndroidModuleExtensionImpl.KIND,
                                                                                 AndroidJpsUtil.ANDROID_FACET_TYPE_ID,
                                                                                 AndroidJpsUtil.ANDROID_FACET_NAME) {
      @Override
      public JpsAndroidModuleExtension loadExtension(@NotNull Element facetConfigurationElement,
                                                     String name,
                                                     JpsElement parent, JpsModule module) {
        return new JpsAndroidModuleExtensionImpl(XmlSerializer.deserialize(facetConfigurationElement, JpsAndroidModuleProperties.class));
      }

      @Override
      protected void saveExtension(JpsAndroidModuleExtension extension, Element facetConfigurationTag, JpsModule module) {
        XmlSerializer.serializeInto(((JpsAndroidModuleExtensionImpl)extension).getProperties(), facetConfigurationTag);
      }
    });
  private static final JpsSdkPropertiesSerializer<JpsSimpleElement<JpsAndroidSdkProperties>> SDK_PROPERTIES_LOADER =
    new JpsSdkPropertiesSerializer<JpsSimpleElement<JpsAndroidSdkProperties>>("Android SDK", JpsAndroidSdkType.INSTANCE) {
      @NotNull
      @Override
      public JpsSimpleElement<JpsAndroidSdkProperties> loadProperties(@Nullable Element propertiesElement) {
        String buildTarget;
        String jdkName;
        if (propertiesElement != null) {
          buildTarget = propertiesElement.getAttributeValue("sdk");
          jdkName = propertiesElement.getAttributeValue("jdk");
        }
        else {
          buildTarget = null;
          jdkName = null;
        }
        return JpsElementFactory.getInstance().createSimpleElement(new JpsAndroidSdkProperties(buildTarget, jdkName));
      }

      @Override
      public void saveProperties(@NotNull JpsSimpleElement<JpsAndroidSdkProperties> properties, @NotNull Element element) {
        String jdkName = properties.getData().getJdkName();
        if (jdkName != null) {
          element.setAttribute("jdk", jdkName);
        }
        String buildTarget = properties.getData().getBuildTargetHashString();
        if (buildTarget != null) {
          element.setAttribute("sdk", buildTarget);
        }
      }
    };

  @NotNull
  @Override
  public List<? extends JpsFacetConfigurationSerializer<?>> getFacetConfigurationSerializers() {
    return FACET_PROPERTIES_LOADERS;
  }

  @NotNull
  @Override
  public List<? extends JpsPackagingElementSerializer<?>> getPackagingElementSerializers() {
    return Collections.singletonList(new JpsAndroidFinalPackageElementSerializer());
  }

  @NotNull
  @Override
  public List<? extends JpsArtifactPropertiesSerializer<?>> getArtifactTypePropertiesSerializers() {
    return Collections.singletonList(new JpsAndroidApplicationArtifactPropertiesSerializer());
  }

  @NotNull
  @Override
  public List<? extends JpsProjectExtensionSerializer> getProjectExtensionSerializers() {
    return Collections.singletonList(new JpsAndroidDexSettingsSerializer());
  }

  @NotNull
  @Override
  public List<? extends JpsSdkPropertiesSerializer<?>> getSdkPropertiesSerializers() {
    return Arrays.asList(SDK_PROPERTIES_LOADER);
  }
}
