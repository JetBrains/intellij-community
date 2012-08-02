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

import com.intellij.android.designer.model.PropertyParser;
import com.intellij.android.designer.model.RadCustomViewComponent;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.propertyTable.editors.ResourceEditor;
import com.intellij.designer.model.PropertiesContainer;
import com.intellij.designer.model.Property;
import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.designer.propertyTable.PropertyRenderer;
import com.intellij.designer.propertyTable.renderers.LabelPropertyRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class CustomViewProperty extends Property<RadCustomViewComponent> implements IXmlAttributeLocator {
  private static final String JAVA_DOC = JavadocParser.build("view:class", "The fully qualified name of the class.");
  private final PropertyRenderer myRenderer = new LabelPropertyRenderer(null);
  private final PropertyEditor myEditor = new ResourceEditor(null, Collections.<AttributeFormat>emptySet(), null) {
    @Override
    protected void showDialog() {
      String view = RadCustomViewComponent.chooseView(myRootComponent);
      if (view != null) {
        setValue(view);
      }
    }
  };

  public CustomViewProperty() {
    super(null, "view:class");
    setImportant(true);
  }

  @Override
  public Property<RadCustomViewComponent> createForNewPresentation(@Nullable Property parent, @NotNull String name) {
    return null;
  }

  @Override
  public Object getValue(@NotNull RadCustomViewComponent component) throws Exception {
    String viewClass = component.getViewClass();
    return viewClass == null ? "" : viewClass;
  }

  @Override
  public void setValue(@NotNull final RadCustomViewComponent component, final Object value) throws Exception {
    if (StringUtil.isEmpty((String)value)) {
      return;
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        XmlTag tag = component.getTag();
        if ("view".equals(tag.getName())) {
          tag.setAttribute("class", (String)value);
        }
        else {
          tag.setName((String)value);
        }
      }
    });

    component.extractClientProperty(RadCustomViewComponent.MODEL_KEY);
    component.setProperties(Collections.<Property>emptyList());

    PropertyParser propertyParser = component.getRoot().getClientProperty(PropertyParser.KEY);
    propertyParser.load(component);
  }

  @Override
  public boolean isDefaultValue(@NotNull RadCustomViewComponent component) throws Exception {
    return false;
  }

  @Override
  public void setDefaultValue(@NotNull RadCustomViewComponent component) throws Exception {
  }

  @Override
  public boolean availableFor(List<PropertiesContainer> components) {
    return false;
  }

  @Override
  public boolean needRefreshPropertyList() {
    return true;
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
    return JAVA_DOC;
  }

  @Override
  public boolean checkAttribute(RadViewComponent component, XmlAttribute attribute) {
    return component.getTag().getAttribute("class") == attribute;
  }
}