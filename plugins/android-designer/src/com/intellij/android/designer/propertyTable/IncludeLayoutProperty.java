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
import com.intellij.android.designer.model.RadIncludeLayout;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.propertyTable.editors.ResourceEditor;
import com.intellij.android.designer.propertyTable.renderers.ResourceRenderer;
import com.intellij.designer.model.PropertiesContainer;
import com.intellij.designer.model.Property;
import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.designer.propertyTable.PropertyRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * @author Alexander Lobas
 */
public class IncludeLayoutProperty extends Property<RadViewComponent> implements IXmlAttributeLocator {
  public static final String NAME = "layout:xml";
  public static ResourceType[] TYPES = new ResourceType[]{ResourceType.LAYOUT};
  private static final Set<AttributeFormat> FORMATS = EnumSet.of(AttributeFormat.Reference);
  public static final Property INSTANCE = new IncludeLayoutProperty();
  private static final String JAVA_DOC = JavadocParser.build(NAME, "Reference to the layout file you wish to include.");

  private final PropertyRenderer myRenderer = new ResourceRenderer(FORMATS);
  private final PropertyEditor myEditor = new ResourceEditor(TYPES, FORMATS, null);

  private IncludeLayoutProperty() {
    super(null, NAME);
    setImportant(true);
  }

  @Override
  public Property<RadViewComponent> createForNewPresentation(@Nullable Property parent, @NotNull String name) {
    return null;
  }

  @Override
  public Object getValue(@NotNull RadViewComponent component) throws Exception {
    String layout = component.getTag().getAttributeValue("layout");
    return layout == null ? "" : layout;
  }

  @Override
  public void setValue(@NotNull final RadViewComponent component, final Object value) throws Exception {
    if (!StringUtil.isEmpty((String)value)) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          ((RadIncludeLayout)component).clearViewInfoCount();
          component.getTag().setAttribute("layout", (String)value);
        }
      });
    }
  }

  @Override
  public boolean availableFor(List<PropertiesContainer> components) {
    return false;
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
    return component.getTag().getAttribute("layout") == attribute;
  }
}