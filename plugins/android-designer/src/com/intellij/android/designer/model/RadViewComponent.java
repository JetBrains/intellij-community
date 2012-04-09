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
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.propertyTable.Property;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.hash.HashMap;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Alexander Lobas
 */
public class RadViewComponent extends RadComponent {
  private final List<RadComponent> myChildren = new ArrayList<RadComponent>();
  private ViewInfo myViewInfo;
  private Component myNativeComponent;
  private final Rectangle myBounds = new Rectangle();
  private Rectangle myMargins;
  private XmlTag myTag;
  private List<Property> myProperties;

  public XmlTag getTag() {
    return myTag;
  }

  public void setTag(XmlTag tag) {
    myTag = tag;
  }

  @Override
  public List<RadComponent> getChildren() {
    return myChildren;
  }

  public ViewInfo getViewInfo() {
    return myViewInfo;
  }

  public void setViewInfo(ViewInfo viewInfo) {
    myViewInfo = viewInfo;
    myMargins = null;
  }

  public Rectangle getMargins() {
    if (myMargins == null) {
      myMargins = new Rectangle();

      try {
        Object layoutParams = myViewInfo.getLayoutParamsObject();
        Class<?> layoutClass = layoutParams.getClass();

        myMargins.x = layoutClass.getField("leftMargin").getInt(layoutParams);
        myMargins.y = layoutClass.getField("topMargin").getInt(layoutParams);
        myMargins.width = layoutClass.getField("rightMargin").getInt(layoutParams);
        myMargins.height = layoutClass.getField("bottomMargin").getInt(layoutParams);
      }
      catch (Throwable e) {
      }
    }
    return myMargins;
  }

  @Override
  public Rectangle getBounds() {
    return myBounds;
  }

  @Override
  public Rectangle getBounds(Component relativeTo) {
    return SwingUtilities.convertRectangle(myNativeComponent, myBounds, relativeTo);
  }

  public void setBounds(int x, int y, int width, int height) {
    myBounds.setBounds(x, y, width, height);
  }

  public Component getNativeComponent() {
    return myNativeComponent;
  }

  public void setNativeComponent(Component nativeComponent) {
    myNativeComponent = nativeComponent;
  }

  @Override
  public Point convertPoint(Component component, int x, int y) {
    return SwingUtilities.convertPoint(component, x, y, myNativeComponent);
  }

  @Override
  public void delete() throws Exception {
    getParent().getChildren().remove(this);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myTag.delete();
      }
    });
  }

  @Override
  public List<Property> getProperties() {
    return myProperties;
  }

  public void setProperties(List<Property> properties) {
    myProperties = properties;
  }

  @Override
  public void copyTo(Element parent) throws Exception {
    // skip root
    if (getParent() != null) {
      Element component = new Element("component");
      component.setAttribute("tag", myTag.getName());

      XmlAttribute[] attributes = myTag.getAttributes();
      if (attributes.length > 0) {
        Element properties = new Element("properties");
        component.addContent(properties);

        Map<String, Element> namespaces = new HashMap<String, Element>();

        for (XmlAttribute attribute : attributes) {
          String namespace = attribute.getNamespacePrefix();
          if (namespace.length() == 0) {
            properties.setAttribute(attribute.getName(), attribute.getValue());
          }
          else {
            Element element = namespaces.get(namespace);
            if (element == null) {
              element = new Element(namespace);
              namespaces.put(namespace, element);
            }

            element.setAttribute(attribute.getLocalName(), attribute.getValue());
          }
        }

        for (Element element : namespaces.values()) {
          properties.addContent(element);
        }
      }

      parent.addContent(component);
      parent = component;
    }

    for (RadComponent child : myChildren) {
      child.copyTo(parent);
    }
  }
}