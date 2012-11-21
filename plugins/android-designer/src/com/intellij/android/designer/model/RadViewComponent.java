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
import com.android.SdkConstants;
import com.intellij.designer.model.*;
import com.intellij.designer.propertyTable.PropertyTable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.hash.HashMap;
import org.jdom.Element;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Alexander Lobas
 */
public class RadViewComponent extends RadVisualComponent {
  private final List<RadComponent> myChildren = new ArrayList<RadComponent>();
  protected ViewInfo myViewInfo;
  private Rectangle myMargins;
  private XmlTag myTag;
  private List<Property> myProperties;

  public XmlTag getTag() {
    if (myTag != null && (myTag.getParent() == null || !myTag.isValid())) {
      return EmptyXmlTag.INSTANCE;
    }
    return myTag;
  }

  public void setTag(XmlTag tag) {
    myTag = tag;
  }

  public void updateTag(XmlTag tag) {
    setTag(tag);

    int size = myChildren.size();
    XmlTag[] tags = tag.getSubTags();

    for (int i = 0; i < size; i++) {
      RadViewComponent child = (RadViewComponent)myChildren.get(i);
      child.updateTag(tags[i]);
    }
  }

  public String getCreationXml() {
    throw new UnsupportedOperationException();
  }

  public ViewInfo getViewInfo() {
    return myViewInfo;
  }

  public void setViewInfo(ViewInfo viewInfo) {
    myViewInfo = viewInfo;
    myMargins = null;
  }

  public int getViewInfoCount() {
    return 1;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public String ensureId() {
    String id = getId();
    if (id == null) {
      id = IdManager.get(this).createId(this);
    }
    return id;
  }

  public String getId() {
    String idValue = getTag().getAttributeValue("id", SdkConstants.NS_RESOURCES);
    return StringUtil.isEmpty(idValue) ? null : idValue;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public int getBaseline() {
    try {
      Object viewObject = myViewInfo.getViewObject();
      return (Integer)viewObject.getClass().getMethod("getBaseline").invoke(viewObject);
    }
    catch (Throwable e) {
    }

    return -1;
  }

  public Rectangle getMargins() {
    if (myMargins == null) {
      myMargins = new Rectangle();

      try {
        Object layoutParams = myViewInfo.getLayoutParamsObject();
        Class<?> layoutClass = layoutParams.getClass();

        myMargins.x = fixDefault(layoutClass.getField("leftMargin").getInt(layoutParams));
        myMargins.y = fixDefault(layoutClass.getField("topMargin").getInt(layoutParams));
        myMargins.width = fixDefault(layoutClass.getField("rightMargin").getInt(layoutParams));
        myMargins.height = fixDefault(layoutClass.getField("bottomMargin").getInt(layoutParams));
      }
      catch (Throwable e) {
      }
    }
    return myMargins;
  }

  private static int fixDefault(int value) {
    return value == Integer.MIN_VALUE ? 0 : value;
  }

  private static final int WRAP_CONTENT = 0 << 30;

  public void calculateWrapSize(Dimension wrapSize, Rectangle bounds) {
    if (wrapSize.width == -1 || wrapSize.height == -1) {
      try {
        Object viewObject = myViewInfo.getViewObject();
        Class<?> viewClass = viewObject.getClass();

        viewClass.getMethod("forceLayout").invoke(viewObject);
        viewClass.getMethod("measure", int.class, int.class).invoke(viewObject, WRAP_CONTENT, WRAP_CONTENT);

        if (wrapSize.width == -1) {
          wrapSize.width = (Integer)viewClass.getMethod("getMeasuredWidth").invoke(viewObject);
        }
        if (wrapSize.height == -1) {
          wrapSize.height = (Integer)viewClass.getMethod("getMeasuredHeight").invoke(viewObject);
        }
      }
      catch (Throwable e) {
        if (wrapSize.width == -1) {
          wrapSize.width = bounds.width;
        }
        if (wrapSize.height == -1) {
          wrapSize.height = bounds.height;
        }
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public List<RadComponent> getChildren() {
    return myChildren;
  }

  @Override
  public void delete() throws Exception {
    IdManager.get(this).removeComponent(this, true);

    removeFromParent();

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
  public List<Property> getInplaceProperties() throws Exception {
    List<Property> properties = super.getInplaceProperties();
    Property idProperty = PropertyTable.findProperty(myProperties, "id");
    if (idProperty != null) {
      properties.add(idProperty);
    }
    return properties;
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

  @Override
  public RadComponent morphingTo(MetaModel target) throws Exception {
    return new ComponentMorphingTool(this, this, target, null).result();
  }
}