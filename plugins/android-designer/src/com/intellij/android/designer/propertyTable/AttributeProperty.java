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
package com.intellij.android.designer.propertyTable;

import com.android.resources.ResourceType;
import com.android.SdkConstants;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.propertyTable.editors.EventHandlerEditor;
import com.intellij.android.designer.propertyTable.editors.ResourceEditor;
import com.intellij.android.designer.propertyTable.editors.StringsComboEditor;
import com.intellij.android.designer.propertyTable.renderers.EventHandlerRenderer;
import com.intellij.android.designer.propertyTable.renderers.ResourceRenderer;
import com.intellij.designer.model.Property;
import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.designer.propertyTable.PropertyRenderer;
import com.intellij.designer.propertyTable.editors.TextEditor;
import com.intellij.designer.propertyTable.renderers.LabelPropertyRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author Alexander Lobas
 */
public class AttributeProperty extends Property<RadViewComponent> implements IXmlAttributeLocator {
  protected final AttributeDefinition myDefinition;
  private final PropertyRenderer myRenderer;
  private final PropertyEditor myEditor;
  private String myTooltip;
  private String myJavadocText;

  public AttributeProperty(@NotNull String name, @NotNull AttributeDefinition definition) {
    this(null, name, definition);
  }

  public AttributeProperty(@Nullable Property parent, @NotNull String name, @NotNull AttributeDefinition definition) {
    super(parent, name);
    myDefinition = definition;

    Set<AttributeFormat> formats = definition.getFormats();

    if ("onClick".equals(getName())) {
      myRenderer = new EventHandlerRenderer(formats);
      myEditor = new EventHandlerEditor();
      return;
    }
    if (formats.size() == 1) {
      if (formats.contains(AttributeFormat.Float)) {
        myRenderer = new LabelPropertyRenderer(null);
        myEditor = new TextEditor();
        return;
      }
      if (formats.contains(AttributeFormat.Enum)) {
        myRenderer = new LabelPropertyRenderer(null);
        myEditor = new StringsComboEditor(definition.getValues());
        return;
      }
    }
    myRenderer = createResourceRenderer(definition, formats);
    myEditor = createResourceEditor(definition, formats);
  }

  protected PropertyRenderer createResourceRenderer(AttributeDefinition definition, Set<AttributeFormat> formats) {
    return new ResourceRenderer(formats);
  }

  protected PropertyEditor createResourceEditor(AttributeDefinition definition, Set<AttributeFormat> formats) {
    String type = AndroidDomUtil.SPECIAL_RESOURCE_TYPES.get(definition.getName());
    if (type == null) {
      return new ResourceEditor(formats, definition.getValues());
    }
    return new ResourceEditor(new ResourceType[]{ResourceType.getEnum(type)}, formats, definition.getValues());
  }

  @Override
  public Property<RadViewComponent> createForNewPresentation(@Nullable Property parent, @NotNull String name) {
    return new AttributeProperty(parent, name, myDefinition);
  }

  @Override
  public String getTooltip() {
    if (myTooltip == null) {
      myTooltip = myDefinition.getFormats().toString();
      myTooltip = myTooltip.substring(1, myTooltip.length() - 1);
    }
    return myTooltip;
  }

  @Override
  public Object getValue(@NotNull RadViewComponent component) throws Exception {
    Object value = null;

    XmlAttribute attribute = getAttribute(component);
    if (attribute != null) {
      value = attribute.getValue();
    }

    return value == null ? "" : value;
  }

  @Override
  public void setValue(@NotNull final RadViewComponent component, final Object value) throws Exception {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        if (StringUtil.isEmpty((String)value)) {
          XmlAttribute attribute = getAttribute(component);
          if (attribute != null) {
            attribute.delete();
          }
        }
        else {
          component.getTag().setAttribute(myDefinition.getName(), SdkConstants.NS_RESOURCES, (String)value);
        }
      }
    });
  }

  @Override
  public boolean isDefaultValue(@NotNull RadViewComponent component) throws Exception {
    return getAttribute(component) == null;
  }

  @Override
  public void setDefaultValue(@NotNull RadViewComponent component) throws Exception {
    if (getAttribute(component) != null) {
      setValue(component, null);
    }
  }

  @Nullable
  private XmlAttribute getAttribute(RadViewComponent component) {
    return component.getTag().getAttribute(myDefinition.getName(), SdkConstants.NS_RESOURCES);
  }

  @Override
  public boolean checkAttribute(RadViewComponent component, XmlAttribute attribute) {
    return getAttribute(component) == attribute;
  }

  @NotNull
  @Override
  public PropertyRenderer getRenderer() {
    return myRenderer;
  }

  @Override
  public PropertyEditor getEditor() {
    return myEditor;
  }

  @Override
  public String getJavadocText() {
    if (myJavadocText == null) {
      String javadocText = myDefinition.getDocValue();
      if (javadocText != null) {
        myJavadocText = JavadocParser.build(getName(), javadocText);
      }
    }
    return myJavadocText;
  }
}