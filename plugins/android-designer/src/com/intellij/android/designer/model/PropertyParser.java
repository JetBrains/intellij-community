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
import com.intellij.android.designer.propertyTable.*;
import com.intellij.designer.model.*;
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

  private MetaManager myMetaManager;
  private AttributeDefinitions myDefinitions;
  private ProjectClassLoader myClassLoader;
  private Map<String, List<Property>> myCachedProperties;

  public PropertyParser(Module module, IAndroidTarget target) throws Exception {
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
    MetaModel model = component.getMetaModelForProperties();
    String target = model.getTarget();
    if (target == null) {
      ViewInfo info = component.getViewInfo();
      if (info == null) {
        component.setProperties(Collections.<Property>emptyList());
      }
      else {
        Class<?> componentClass = configureClass(myClassLoader.loadClass(info.getClassName()));
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
        MetaModel[] models = new MetaModel[layoutParams.length];
        models[0] = parent.getMetaModelForProperties();

        for (int i = 1; i < layoutParams.length; i++) {
          if (models[i - 1] == null) {
            break;
          }
          String extendTarget = models[i - 1].getTarget();
          if (extendTarget == null) {
            break;
          }

          Class<?> superClass = myClassLoader.loadClass(extendTarget).getSuperclass();
          if (superClass != null) {
            superClass = configureClass(superClass);
            models[i] = myMetaManager.getModelByTarget(superClass.getName());
          }
        }

        List<Property> properties = loadLayoutProperties(layoutParams, 0, models);
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

      if ("View".equals(component)) {
        properties.add(new StyleProperty());
      }

      StyleableDefinition definitions = myDefinitions.getStyleableByName(component);
      if (definitions != null) {
        boolean padding = false;

        for (AttributeDefinition definition : definitions.getAttributes()) {
          String name = definition.getName();
          Set<AttributeFormat> formats = definition.getFormats();
          Property property;

          if ("padding".equals(name) && "View".equals(component)) {
            padding = true;
          }
          if (formats.contains(AttributeFormat.Flag)) {
            property = new FlagProperty(name, definition);
          }
          else {
            if ("id".equals(name) && "View".equals(component)) {
              property = new IdProperty(name, definition);
            }
            else {
              property = new AttributeProperty(name, definition);
            }
          }

          if (model != null) {
            model.decorate(property, name);
          }
          properties.add(property);
        }

        if (padding) {
          CompoundDimensionProperty paddingProperty = new CompoundDimensionProperty("padding");
          moveProperties(properties, paddingProperty,
                         "padding", "all",
                         "paddingLeft", "left",
                         "paddingTop", "top",
                         "paddingRight", "right",
                         "paddingBottom", "bottom");
          if (model != null) {
            paddingProperty.decorate(model);
          }
          properties.add(paddingProperty);
        }
      }

      Class<?> superComponentClass = componentClass.getSuperclass();
      if (superComponentClass != null) {
        superComponentClass = configureClass(superComponentClass);
        MetaModel superModel = myMetaManager.getModelByTarget(superComponentClass.getName());

        if (model != null && superModel != null && model.getInplaceProperties().isEmpty()) {
          model.setInplaceProperties(superModel.getInplaceProperties());
        }

        List<Property> superProperties = loadWidgetProperties(superComponentClass, superModel);
        for (Property superProperty : superProperties) {
          if (PropertyTable.findProperty(properties, superProperty) == -1) {
            if (model == null) {
              properties.add(superProperty);
            }
            else {
              properties.add(model.decorateWithOverride(superProperty));
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

        if (model != null) {
          for (String topName : model.getTopProperties()) {
            PropertyTable.moveProperty(properties, topName, properties, 0);
          }
        }

        PropertyTable.moveProperty(properties, "style", properties, 0);
      }
    }

    return properties;
  }

  private Class<?> configureClass(Class<?> viewClass) throws Exception {
    if (viewClass.getName().equals("com.android.layoutlib.bridge.MockView")) {
      return myClassLoader.loadClass("android.view.View");
    }
    return viewClass;
  }

  private List<Property> loadLayoutProperties(String[] components, int index, MetaModel[] models) throws Exception {
    String component = components[index];
    MetaModel model = models[index];

    List<Property> properties = myCachedProperties.get(component);

    if (properties == null) {
      properties = new ArrayList<Property>();
      myCachedProperties.put(component, properties);

      StyleableDefinition definitions = myDefinitions.getStyleableByName(component);
      if (definitions != null) {
        boolean margin = false;

        for (AttributeDefinition definition : definitions.getAttributes()) {
          String name = definition.getName();
          boolean important = true;
          Set<AttributeFormat> formats = definition.getFormats();
          Property property;

          if (name.startsWith(LAYOUT_MARGIN_PREFIX)) {
            name = name.substring(LAYOUT_PREFIX.length());
            important = false;
          }
          else if (name.startsWith(LAYOUT_PREFIX)) {
            name = "layout:" + name.substring(LAYOUT_PREFIX.length());
          }

          if ("margin".equals(name) && "ViewGroup_MarginLayout".equals(component)) {
            margin = true;
          }
          if ("layout:width".equals(name) || "layout:height".equals(name)) {
            property = new AttributePropertyWithDefault(name, definition, "wrap_content");
          }
          else if (formats.contains(AttributeFormat.Flag)) {
            if ("layout:gravity".equals(name)) {
              property = new GravityProperty(name, definition);
            }
            else {
              property = new FlagProperty(name, definition);
            }
          }
          else {
            property = new AttributeProperty(name, definition);
          }

          if (model != null) {
            model.decorate(property, name);
          }
          property.setImportant(important);
          properties.add(property);
        }

        if (margin) {
          CompoundDimensionProperty marginProperty = new CompoundDimensionProperty("layout:margin");
          moveProperties(properties, marginProperty,
                         "margin", "all",
                         "marginLeft", "left",
                         "marginTop", "top",
                         "marginRight", "right",
                         "marginBottom", "bottom",
                         "marginStart", "start",
                         "marginEnd", "end");
          if (model != null) {
            marginProperty.decorate(model);
          }
          marginProperty.setImportant(true);
          properties.add(marginProperty);
        }
      }

      if (++index < components.length) {
        for (Property property : loadLayoutProperties(components, index, models)) {
          if (PropertyTable.findProperty(properties, property) == -1) {
            if (model == null) {
              properties.add(property);
            }
            else {
              property = model.decorateWithOverride(property);
              properties.add(property);
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

        PropertyTable.moveProperty(properties, "layout:margin", properties, 0);
        PropertyTable.moveProperty(properties, "layout:gravity", properties, 0);
        PropertyTable.moveProperty(properties, "layout:height", properties, 0);
        PropertyTable.moveProperty(properties, "layout:width", properties, 0);
      }

      if (model != null) {
        Class<RadLayout> layout = model.getLayout();
        if (layout != null) {
          layout.newInstance().configureProperties(properties);
        }
      }
    }

    return properties;
  }

  public static void moveProperties(List<Property> source, Property destination, String... names) {
    List<Property> children = destination.getChildren(null);
    for (int i = 0; i < names.length; i += 2) {
      Property property = PropertyTable.extractProperty(source, names[i]);
      if (property != null) {
        children.add(property.createForNewPresentation(destination, names[i + 1]));
      }
    }
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
}