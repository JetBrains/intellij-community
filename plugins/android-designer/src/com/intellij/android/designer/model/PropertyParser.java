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
package com.intellij.android.designer.model;

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.sdklib.IAndroidTarget;
import com.intellij.designer.model.MetaManager;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.propertyTable.Property;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.attrs.StyleableDefinition;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.android.uipreview.ProjectClassLoader;
import org.jetbrains.android.uipreview.RenderServiceFactory;

import java.util.*;

/**
 * @author Alexander Lobas
 */
@SuppressWarnings("unchecked")
public class PropertyParser {
  private AttributeDefinitions myDefinitions;
  private ProjectClassLoader myClassLoader;
  private Map<String, List<AttributeDefinition>> myCachedAttributes;

  public PropertyParser(Module module, IAndroidTarget target) throws Exception {
    MetaManager metaManager = ViewsMetaManager.getInstance(module.getProject());
    myCachedAttributes = (Map<String, List<AttributeDefinition>>)metaManager.getCache().get(target.hashString());
    if (myCachedAttributes == null) {
      myCachedAttributes = new HashMap<String, List<AttributeDefinition>>();
      metaManager.getCache().put(target.hashString(), myCachedAttributes);
    }

    AndroidPlatform androidPlatform = AndroidPlatform.getInstance(module);
    AndroidTargetData targetData = androidPlatform.getSdkData().getTargetData(target);
    myDefinitions = targetData.getAttrDefs(module.getProject());

    RenderServiceFactory factory = targetData.getRenderServiceFactory(module.getProject());
    myClassLoader = new ProjectClassLoader(factory.getLibrary().getClassLoader(), module);
  }

  public void loadRecursive(RadViewComponent component) throws Exception {
    load(component);

    for (RadComponent child : component.getChildren()) {
      loadRecursive((RadViewComponent)child);
    }
  }

  public void load(RadViewComponent component) throws Exception {
    List<Property> properties = new ArrayList<Property>();
    component.setProperties(properties);

    ViewInfo info = component.getViewInfo();
    if (info == null) {
      return;
    }

    List<AttributeDefinition> attributes = loadAttributes(myClassLoader.loadClass(info.getClassName()));
    for (AttributeDefinition attribute : attributes) {
      // TODO
    }
  }

  private List<AttributeDefinition> loadAttributes(Class<?> componentClass) {
    String component = componentClass.getSimpleName();
    List<AttributeDefinition> attributes = myCachedAttributes.get(component);

    if (attributes == null) {
      attributes = new ArrayList<AttributeDefinition>();

      StyleableDefinition attributeDefs = myDefinitions.getStyleableByName(component);
      if (attributeDefs != null) {
        attributes.addAll(attributeDefs.getAttributes());
      }

      Class<?> superComponentClass = componentClass.getSuperclass();
      if (superComponentClass != null) {
        attributes.addAll(loadAttributes(superComponentClass));
      }

      if (!attributes.isEmpty()) {
        Collections.sort(attributes, new Comparator<AttributeDefinition>() {
          @Override
          public int compare(AttributeDefinition a1, AttributeDefinition a2) {
            return a1.getName().compareTo(a2.getName());
          }
        });
      }

      myCachedAttributes.put(component, attributes);
    }

    return attributes;
  }
}