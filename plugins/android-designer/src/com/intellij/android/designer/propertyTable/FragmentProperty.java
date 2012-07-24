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

import com.android.sdklib.SdkConstants;
import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.propertyTable.Property;
import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.designer.propertyTable.PropertyRenderer;
import com.intellij.designer.propertyTable.renderers.LabelPropertyRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Alexander Lobas
 */
public class FragmentProperty extends Property<RadViewComponent> implements IXmlAttributeLocator {
  private final String myAttribute;
  private final PropertyRenderer myRenderer = new LabelPropertyRenderer(null);
  private final PropertyEditor myEditor;
  private final String myJavadocText;

  public FragmentProperty(@NotNull String name, PropertyEditor editor, String javadocText) {
    super(null, name);
    myEditor = editor;
    myJavadocText = javadocText;
    setImportant(true);
    myAttribute = name;
  }

  @Override
  public Property<RadViewComponent> createForNewPresentation(@Nullable Property parent, @NotNull String name) {
    return null;
  }

  @Override
  public Object getValue(RadViewComponent component) throws Exception {
    String value = component.getTag().getAttributeValue(myAttribute, SdkConstants.NS_RESOURCES);
    return value == null ? "" : value;
  }

  @Override
  public void setValue(final RadViewComponent component, final Object value) throws Exception {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        if (StringUtil.isEmpty((String)value)) {
          ModelParser.deleteAttribute(component, myAttribute);
        }
        else {
          component.getTag().setAttribute(myAttribute, SdkConstants.NS_RESOURCES, (String)value);
        }
      }
    });
  }

  @Override
  public boolean isDefaultValue(RadViewComponent component) throws Exception {
    return component.getTag().getAttribute(myAttribute, SdkConstants.NS_RESOURCES) == null;
  }

  @Override
  public void setDefaultValue(RadViewComponent component) throws Exception {
    if (component.getTag().getAttribute(myAttribute, SdkConstants.NS_RESOURCES) != null) {
      setValue(component, null);
    }
  }

  @Override
  public boolean availableFor(List<RadComponent> components) {
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
    return myJavadocText;
  }

  @Override
  public boolean checkAttribute(RadViewComponent component, XmlAttribute attribute) {
    return component.getTag().getAttribute(myAttribute, SdkConstants.NS_RESOURCES) == attribute;
  }
}