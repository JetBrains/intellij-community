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
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.propertyTable.editors.ResourceEditor;
import com.intellij.android.designer.propertyTable.renderers.ResourceRenderer;
import com.intellij.designer.model.Property;
import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.designer.propertyTable.PropertyRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

/**
 * @author Alexander Lobas
 */
public class StyleProperty extends Property<RadViewComponent> implements IXmlAttributeLocator {
  public static final ResourceType[] STYLE_TYPES = new ResourceType[]{ResourceType.STYLE};

  private final PropertyRenderer myRenderer = new ResourceRenderer(Collections.<AttributeFormat>emptySet());
  private final PropertyEditor myEditor = new ResourceEditor(STYLE_TYPES, Collections.<AttributeFormat>emptySet(), null);
  private final String myJavadocText = JavadocParser.build("style", "A reference to a custom style");

  public StyleProperty() {
    super(null, "style");
    setImportant(true);
  }

  @Override
  public Property<RadViewComponent> createForNewPresentation(@Nullable Property parent, @NotNull String name) {
    return null;
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
          component.getTag().setAttribute("style", (String)value);
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
  private static XmlAttribute getAttribute(RadViewComponent component) {
    return component.getTag().getAttribute("style");
  }

  @Override
  public boolean checkAttribute(RadViewComponent component, XmlAttribute attribute) {
    return getAttribute(component) == attribute;
  }

  @Override
  public String getJavadocText() {
    return myJavadocText;
  }
}