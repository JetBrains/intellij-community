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
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.intellij.android.designer.propertyTable.AttributeProperty;
import com.intellij.android.designer.propertyTable.FlagProperty;
import com.intellij.android.designer.propertyTable.PaddingProperty;
import com.intellij.android.designer.propertyTable.editors.ResourceDialog;
import com.intellij.designer.model.MetaManager;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.propertyTable.Property;
import com.intellij.designer.propertyTable.PropertyTable;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.android.dom.attrs.StyleableDefinition;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.android.uipreview.ProjectClassLoader;
import org.jetbrains.android.uipreview.RenderServiceFactory;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Alexander Lobas
 */
@SuppressWarnings("unchecked")
public class PropertyParser {
  public static final String KEY = "PROPERTY_PARSER";

  private final Module myModule;
  private MetaManager myMetaManager;
  private AttributeDefinitions myDefinitions;
  private ProjectClassLoader myClassLoader;
  private Map<String, List<Property>> myCachedProperties;

  public PropertyParser(Module module, IAndroidTarget target) throws Exception {
    myModule = module;
    myMetaManager = ViewsMetaManager.getInstance(module.getProject());
    myCachedProperties = myMetaManager.getCache(target.hashString());
    if (myCachedProperties == null) {
      myMetaManager.setCache(target.hashString(), myCachedProperties = new HashMap<String, List<Property>>());
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
    MetaModel model = component.getMetaModel();
    if (info == null) {
      String target = model.getTarget();
      if (target == null) {
        component.setProperties(Collections.<Property>emptyList());
      }
      else {
        component.setProperties(load(myClassLoader.loadClass(target), model));
      }
    }
    else {
      component.setProperties(load(myClassLoader.loadClass(info.getClassName()), model));
    }
  }

  private List<Property> load(Class<?> componentClass, MetaModel model) {
    String component = componentClass.getSimpleName();
    List<Property> properties = myCachedProperties.get(component);

    if (properties == null) {
      properties = new ArrayList<Property>();

      StyleableDefinition definitions = myDefinitions.getStyleableByName(component);
      if (definitions != null) {
        Property padding = null;

        for (AttributeDefinition definition : definitions.getAttributes()) {
          String name = definition.getName();
          Set<AttributeFormat> formats = definition.getFormats();
          Property property;

          if ("padding".equals(name) && "View".equals(component)) {
            property = padding = new PaddingProperty(name, definition);
          }
          else if (formats.contains(AttributeFormat.Flag)) {
            property = new FlagProperty(name, definition);
          }
          else {
            property = new AttributeProperty(name, definition);
          }

          if (model != null) {
            property.setImportant(model.isImportantProperty(name));
            property.setExpert(model.isExpertProperty(name));
            property.setDeprecated(model.isDeprecatedProperty(name));
          }

          properties.add(property);
        }

        if (padding != null) {
          List children = padding.getChildren(null);
          children.add(PropertyTable.extractProperty(properties, "paddingLeft"));
          children.add(PropertyTable.extractProperty(properties, "paddingTop"));
          children.add(PropertyTable.extractProperty(properties, "paddingRight"));
          children.add(PropertyTable.extractProperty(properties, "paddingBottom"));
          children.add(PropertyTable.extractProperty(properties, "paddingStart"));
          children.add(PropertyTable.extractProperty(properties, "paddingEnd"));
        }
      }

      Class<?> superComponentClass = componentClass.getSuperclass();
      if (superComponentClass != null) {
        List<Property> superProperties = load(superComponentClass, myMetaManager.getModelByTarget(superComponentClass.getName()));
        for (Property superProperty : superProperties) {
          if (PropertyTable.findProperty(properties, superProperty) == -1) {
            if (model == null) {
              properties.add(superProperty);
            }
            else {
              String name = superProperty.getName();
              boolean normal = model.isNormalProperty(name);
              boolean important = model.isImportantProperty(name);
              boolean expert = model.isExpertProperty(name);
              boolean deprecated = model.isDeprecatedProperty(name);

              if ((normal && (superProperty.isImportant() || superProperty.isExpert())) ||
                  (important && !superProperty.isImportant()) ||
                  (expert && !superProperty.isExpert()) ||
                  (deprecated && !superProperty.isDeprecated())) {
                Property property = superProperty.createForNewPresentation();
                property.setImportant(important);
                property.setExpert(expert);
                property.setDeprecated(deprecated);
                properties.add(property);
              }
              else {
                properties.add(superProperty);
              }
            }
          }
        }
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

  @NotNull
  public ResourceDialog createResourceDialog(ResourceType[] types) {
    return new ResourceDialog(myModule, types);
  }
}