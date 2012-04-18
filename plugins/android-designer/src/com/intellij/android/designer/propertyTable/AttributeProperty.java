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

import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.propertyTable.editors.ResourceEditor;
import com.intellij.android.designer.propertyTable.editors.StringsComboEditor;
import com.intellij.android.designer.propertyTable.renderers.ResourceRenderer;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.propertyTable.Property;
import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.designer.propertyTable.PropertyRenderer;
import com.intellij.designer.propertyTable.editors.AbstractTextFieldEditor;
import com.intellij.designer.propertyTable.renderers.LabelPropertyRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * @author Alexander Lobas
 */
public class AttributeProperty extends Property<RadViewComponent> {
  protected final AttributeDefinition myDefinition;
  private final PropertyRenderer myRenderer;
  private final PropertyEditor myEditor;

  public AttributeProperty(@NotNull String name, @NotNull AttributeDefinition definition) {
    this(null, name, definition);
  }

  public AttributeProperty(@Nullable Property parent, @NotNull String name, @NotNull AttributeDefinition definition) {
    super(parent, name);
    myDefinition = definition;

    Set<AttributeFormat> formats = definition.getFormats();

    if (formats.size() == 1) {
      if (formats.contains(AttributeFormat.Float)) {
        myRenderer = new LabelPropertyRenderer(null);
        myEditor = new AbstractTextFieldEditor() {
          @Override
          public Object getValue() throws Exception {
            return myTextField.getText();
          }
        };
      }
      else if (formats.contains(AttributeFormat.Enum)) {
        myRenderer = new LabelPropertyRenderer(null);
        myEditor = new StringsComboEditor(definition.getValues());
      }
      else {
        myRenderer = new ResourceRenderer(formats);
        myEditor = new ResourceEditor(formats, definition.getValues());
      }
    }
    else {
      myRenderer = new ResourceRenderer(formats);
      myEditor = new ResourceEditor(formats, definition.getValues());
    }
  }

  @Override
  public Property<RadViewComponent> createForNewPresentation(@Nullable Property parent, @NotNull String name) {
    return new AttributeProperty(parent, name, myDefinition);
  }

  @Override
  public Object getValue(RadViewComponent component) throws Exception {
    Object value = null;

    XmlAttribute attribute = getAttribute(component);
    if (attribute != null) {
      value = attribute.getValue();
    }

    return value == null ? "" : value;
  }

  @Override
  public void setValue(final RadViewComponent component, final Object value) throws Exception {
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
          component.getTag().setAttribute("android:" + myDefinition.getName(), (String)value);
        }
      }
    });
  }

  @Override
  public boolean isDefaultValue(RadViewComponent component) throws Exception {
    return getAttribute(component) == null;
  }

  @Override
  public void setDefaultValue(RadViewComponent component) throws Exception {
    if (getAttribute(component) != null) {
      setValue(component, null);
    }
  }

  @Nullable
  private XmlAttribute getAttribute(RadViewComponent component) {
    return component.getTag().getAttribute("android:" + myDefinition.getName());
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
  public boolean availableFor(List<RadComponent> components) {
    return !"id".equals(getName());
  }
}