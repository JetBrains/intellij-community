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
  public static final String KEY = "PROPERTY_PARSER";

  private AttributeDefinitions myDefinitions;
  private ProjectClassLoader myClassLoader;
  private Map<String, List<Property>> myCachedProperties;

  public PropertyParser(Module module, IAndroidTarget target) throws Exception {
    MetaManager metaManager = ViewsMetaManager.getInstance(module.getProject());
    myCachedProperties = metaManager.getCache(target.hashString());
    if (myCachedProperties == null) {
      metaManager.setCache(target.hashString(), myCachedProperties = new HashMap<String, List<Property>>());
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
    ViewInfo info = component.getViewInfo();
    if (info == null) {
      String target = component.getMetaModel().getTarget();
      if (target == null) {
        component.setProperties(Collections.<Property>emptyList());
      }
      else {
        component.setProperties(load(myClassLoader.loadClass(target)));
      }
    }
    else {
      component.setProperties(load(myClassLoader.loadClass(info.getClassName())));
    }
  }

  private List<Property> load(Class<?> componentClass) {
    String component = componentClass.getSimpleName();
    List<Property> properties = myCachedProperties.get(component);

    if (properties == null) {
      properties = new ArrayList<Property>();

      StyleableDefinition definitions = myDefinitions.getStyleableByName(component);
      if (definitions != null) {
        for (AttributeDefinition definition : definitions.getAttributes()) {
          properties.add(new AttributeProperty(null, definition));
          // TODO
        }
      }

      Class<?> superComponentClass = componentClass.getSuperclass();
      if (superComponentClass != null) {
        properties.addAll(load(superComponentClass));
      }

      if (!properties.isEmpty()) {
        Collections.sort(properties, new Comparator<Property>() {
          @Override
          public int compare(Property p1, Property p2) {
            return p1.getName().compareTo(p2.getName());
          }
        });
      }

      myCachedProperties.put(component, properties);
    }

    return properties;
  }
}