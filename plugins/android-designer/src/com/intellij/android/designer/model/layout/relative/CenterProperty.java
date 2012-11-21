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
package com.intellij.android.designer.model.layout.relative;

import com.android.SdkConstants;
import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.propertyTable.IXmlAttributeLocator;
import com.intellij.android.designer.propertyTable.JavadocParser;
import com.intellij.android.designer.propertyTable.editors.StringsComboEditor;
import com.intellij.designer.model.Property;
import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.designer.propertyTable.PropertyRenderer;
import com.intellij.designer.propertyTable.renderers.LabelPropertyRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Lobas
 */
public class CenterProperty extends Property<RadViewComponent> implements IXmlAttributeLocator {
  private static final String[] COMBO_ITEMS = {"horizontal", "vertical", "both"};
  private static final String[] ATTR_ITEMS = {"layout_centerHorizontal", "layout_centerVertical", "layout_centerInParent"};
  private static final String JAVA_DOC =
    JavadocParser.build("layout:centerInParent", "Set centers this child horizontally or vertically within its parent.");

  private final PropertyRenderer myRenderer = new LabelPropertyRenderer(null);
  private final PropertyEditor myEditor = new StringsComboEditor(COMBO_ITEMS);

  public CenterProperty() {
    super(null, "layout:centerInParent");
    setImportant(true);
  }

  @Override
  public Object getValue(@NotNull RadViewComponent component) throws Exception {
    XmlTag tag = component.getTag();
    boolean[] values = new boolean[3];
    for (int i = 0; i < ATTR_ITEMS.length; i++) {
      values[i] = "true".equals(tag.getAttributeValue(ATTR_ITEMS[i], SdkConstants.NS_RESOURCES));
    }
    if (values[2] || (values[0] && values[1])) {
      return COMBO_ITEMS[2];
    }
    if (values[0]) {
      return COMBO_ITEMS[0];
    }
    if (values[1]) {
      return COMBO_ITEMS[1];
    }
    return null;
  }

  @Override
  public void setValue(@NotNull final RadViewComponent component, Object value) throws Exception {
    final int index = ArrayUtil.indexOf(COMBO_ITEMS, value);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        XmlTag tag = component.getTag();
        for (int i = 0; i < ATTR_ITEMS.length; i++) {
          if (i == index) {
            tag.setAttribute(ATTR_ITEMS[i], SdkConstants.NS_RESOURCES, "true");
          }
          else {
            ModelParser.deleteAttribute(tag, ATTR_ITEMS[i]);
          }
        }
      }
    });
  }

  @Override
  public boolean isDefaultValue(@NotNull RadViewComponent component) throws Exception {
    XmlTag tag = component.getTag();
    for (String attribute : ATTR_ITEMS) {
      if (tag.getAttribute(attribute, SdkConstants.NS_RESOURCES) != null) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void setDefaultValue(@NotNull RadViewComponent component) throws Exception {
    setValue(component, null);
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
  public String getJavadocText() {
    return JAVA_DOC;
  }

  @Override
  public boolean checkAttribute(RadViewComponent component, XmlAttribute attribute) {
    XmlTag tag = component.getTag();
    for (String name : ATTR_ITEMS) {
      if (tag.getAttribute(name, SdkConstants.NS_RESOURCES) == attribute) {
        return true;
      }
    }
    return false;
  }
}