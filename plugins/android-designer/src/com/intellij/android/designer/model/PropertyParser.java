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
import com.intellij.android.designer.propertyTable.AttributePropertyWithDefault;
import com.intellij.android.designer.propertyTable.CompoundProperty;
import com.intellij.android.designer.propertyTable.FlagProperty;
import com.intellij.android.designer.propertyTable.editors.ResourceDialog;
import com.intellij.designer.model.MetaManager;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadLayout;
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

  private static final String[] DEFAULT_LAYOUT_PARAMS = {"ViewGroup_Layout"};
  private static final String LAYOUT_PREFIX = "layout_";
  private static final String LAYOUT_MARGIN_PREFIX = "layout_margin";

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
    MetaModel model = component.getMetaModel();
    String target = model.getTarget();
    if (target == null) {
      ViewInfo info = component.getViewInfo();
      if (info == null) {
        component.setProperties(Collections.<Property>emptyList());
      }
      else {
        Class<?> componentClass = myClassLoader.loadClass(info.getClassName());
        if (componentClass.getName().equals("com.android.layoutlib.bridge.MockView")) {
          componentClass = myClassLoader.loadClass("android.view.View");
        }
        component.setProperties(loadWidgetProperties(componentClass, model));
      }
    }
    else {
      component.setProperties(loadWidgetProperties(myClassLoader.loadClass(target), model));
    }

    RadComponent parent = component.getParent();
    if (parent != null) {
      String[] layoutParams = null;
      RadLayout layout = parent.getLayout();

      if (layout instanceof RadViewLayoutWithData) {
        layoutParams = ((RadViewLayoutWithData)layout).getLayoutParams();
      }
      else if (parent == parent.getRoot()) {
        layoutParams = DEFAULT_LAYOUT_PARAMS;
      }

      if (layoutParams != null) {
        List<Property> properties = loadLayoutProperties(layoutParams, 0, parent.getMetaModel());

        if (!properties.isEmpty()) {
          properties = new ArrayList<Property>(properties);
          properties.addAll(component.getProperties());
          component.setProperties(properties);
        }
      }
    }
  }

  private List<Property> loadWidgetProperties(Class<?> componentClass, MetaModel model) throws Exception {
    String component = componentClass.getSimpleName();

    List<Property> properties = myCachedProperties.get(component);

    if (properties == null) {
      properties = new ArrayList<Property>();
      myCachedProperties.put(component, properties);

      StyleableDefinition definitions = myDefinitions.getStyleableByName(component);
      if (definitions != null) {
        Property padding = null;

        for (AttributeDefinition definition : definitions.getAttributes()) {
          String name = definition.getName();
          Set<AttributeFormat> formats = definition.getFormats();
          Property property;

          if ("padding".equals(name) && "View".equals(component)) {
            property = padding = new CompoundProperty(name, definition);
          }
          else if (formats.contains(AttributeFormat.Flag)) {
            property = new FlagProperty(name, definition, model);
          }
          else {
            property = new AttributeProperty(name, definition);
          }

          if (model != null) {
            model.decorate(property, name);
          }

          properties.add(property);
        }

        if (padding != null) {
          List<Property> children = padding.getChildren(null);

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
        if (superComponentClass.getName().equals("com.android.layoutlib.bridge.MockView")) {
          superComponentClass = myClassLoader.loadClass("android.view.View");
        }

        List<Property> superProperties = loadWidgetProperties(superComponentClass,
                                                              myMetaManager.getModelByTarget(superComponentClass.getName()));
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
    }

    return properties;
  }

  private List<Property> loadLayoutProperties(String[] components, int index, MetaModel model) throws Exception {
    String component = components[index];

    List<Property> properties = myCachedProperties.get(component);

    if (properties == null) {
      properties = new ArrayList<Property>();
      myCachedProperties.put(component, properties);

      StyleableDefinition definitions = myDefinitions.getStyleableByName(component);
      if (definitions != null) {
        Property margin = null;

        for (AttributeDefinition definition : definitions.getAttributes()) {
          String name = definition.getName();
          boolean important = true;
          Set<AttributeFormat> formats = definition.getFormats();
          Property property;

          if (name.startsWith(LAYOUT_MARGIN_PREFIX) && name.length() > LAYOUT_MARGIN_PREFIX.length()) {
            name = name.substring(LAYOUT_PREFIX.length());
            important = false;
          }
          else if (name.startsWith(LAYOUT_PREFIX)) {
            name = "layout:" + name.substring(LAYOUT_PREFIX.length());
          }

          if ("layout:margin".equals(name) && "ViewGroup_MarginLayout".equals(component)) {
            property = margin = new CompoundProperty(name, definition);
          }
          else if ("layout:width".equals(name) || "layout:height".equals(name)) {
            property = new AttributePropertyWithDefault(name, definition, "wrap_content");
          }
          else if (formats.contains(AttributeFormat.Flag)) {
            property = new FlagProperty(name, definition, model);
          }
          else {
            property = new AttributeProperty(name, definition);
          }

          property.setImportant(important);
          properties.add(property);
        }

        if (margin != null) {
          List<Property> children = margin.getChildren(null);

          PropertyTable.moveProperty(properties, "marginLeft", children, -1);
          PropertyTable.moveProperty(properties, "marginTop", children, -1);
          PropertyTable.moveProperty(properties, "marginRight", children, -1);
          PropertyTable.moveProperty(properties, "marginBottom", children, -1);
          PropertyTable.moveProperty(properties, "marginStart", children, -1);
          PropertyTable.moveProperty(properties, "marginEnd", children, -1);

          if (model != null) {
            for (Property child : children) {
              model.decorate(child, "layout:margin." + child.getName());
            }
          }
        }
      }

      if (++index < components.length) {
        for (Property property : loadLayoutProperties(components, index, model)) {
          if (PropertyTable.findProperty(properties, property) == -1) {
            properties.add(property);
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

        PropertyTable.moveProperty(properties, "layout:margin", properties, 0);
        PropertyTable.moveProperty(properties, "layout:gravity", properties, 0);
        PropertyTable.moveProperty(properties, "layout:height", properties, 0);
        PropertyTable.moveProperty(properties, "layout:width", properties, 0);
      }
    }

    return properties;
  }

  public boolean isAssignableFrom(MetaModel base, MetaModel test) {
    try {
      Class<?> baseClass = myClassLoader.loadClass(base.getTarget());
      Class<?> testClass = myClassLoader.loadClass(test.getTarget());
      return baseClass.isAssignableFrom(testClass);
    }
    catch (Throwable e) {
    }
    return false;
  }

  @NotNull
  public ResourceDialog createResourceDialog(ResourceType[] types) {
    return new ResourceDialog(myModule, types);
  }
}