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

import com.android.SdkConstants;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.Property;
import com.intellij.designer.propertyTable.IPropertyDecorator;
import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.designer.propertyTable.PropertyRenderer;
import com.intellij.designer.propertyTable.editors.BooleanEditor;
import com.intellij.designer.propertyTable.renderers.BooleanRenderer;
import com.intellij.designer.propertyTable.renderers.LabelPropertyRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Alexander Lobas
 */
public class FlagProperty extends Property<RadViewComponent> implements IPropertyDecorator, IXmlAttributeLocator {
  private final PropertyRenderer myRenderer = new LabelPropertyRenderer(null);
  protected final AttributeDefinition myDefinition;
  protected final List<Property<RadViewComponent>> myOptions = new ArrayList<Property<RadViewComponent>>();
  private String myJavadocText;

  public FlagProperty(@NotNull String name, @NotNull AttributeDefinition definition) {
    super(null, name);
    myDefinition = definition;

    for (String option : definition.getValues()) {
      myOptions.add(new OptionProperty(this, option, option));
    }
  }

  @Override
  public Property<RadViewComponent> createForNewPresentation(@Nullable Property parent, @NotNull String name) {
    return new FlagProperty(name, myDefinition);
  }

  @Override
  public void decorate(@NotNull MetaModel model) {
    String name = getName();
    for (Property option : myOptions) {
      model.decorate(option, name + "." + option.getName());
    }
  }

  @NotNull
  @Override
  public List<Property<RadViewComponent>> getChildren(@Nullable RadViewComponent component) {
    return myOptions;
  }

  @Override
  public Object getValue(@NotNull RadViewComponent component) throws Exception {
    StringBuilder value = new StringBuilder("[");
    Set<String> options = getOptions(component);
    int index = 0;
    for (Property option : myOptions) {
      if (options.contains(((OptionProperty)option).getValueName())) {
        if (index++ > 0) {
          value.append(", ");
        }
        value.append(option.getName());
      }
    }
    return value.append("]").toString();
  }

  @Override
  public boolean isDefaultValue(@NotNull RadViewComponent component) throws Exception {
    return getAttribute(component) == null;
  }

  @Override
  public void setDefaultValue(@NotNull RadViewComponent component) throws Exception {
    final XmlAttribute attribute = getAttribute(component);
    if (attribute != null) {
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          attribute.delete();
        }
      });
    }
  }

  @NotNull
  @Override
  public PropertyRenderer getRenderer() {
    return myRenderer;
  }

  @Override
  public PropertyEditor getEditor() {
    return null;
  }

  @Nullable
  protected XmlAttribute getAttribute(RadViewComponent component) {
    return component.getTag().getAttribute(myDefinition.getName(), SdkConstants.NS_RESOURCES);
  }

  @Override
  public boolean checkAttribute(RadViewComponent component, XmlAttribute attribute) {
    return getAttribute(component) == attribute;
  }

  protected Set<String> getOptions(RadViewComponent component) throws Exception {
    String value = component.getTag().getAttributeValue(myDefinition.getName(), SdkConstants.NS_RESOURCES);
    if (value == null) {
      return Collections.emptySet();
    }
    Set<String> options = new HashSet<String>();
    for (String option : StringUtil.split(value, "|")) {
      options.add(option.trim());
    }
    return options;
  }

  private boolean isOption(RadViewComponent component, String name) throws Exception {
    return getOptions(component).contains(name);
  }

  private void setOption(final RadViewComponent component, String name, boolean set) throws Exception {
    final Set<String> options = new HashSet<String>(getOptions(component));
    if (set) {
      if (!options.add(name)) {
        return;
      }
    }
    else if (!options.remove(name)) {
      return;
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        if (options.isEmpty()) {
          XmlAttribute attribute = getAttribute(component);
          if (attribute != null) {
            attribute.delete();
          }
        }
        else {
          component.getTag().setAttribute(myDefinition.getName(), SdkConstants.NS_RESOURCES, StringUtil.join(options, "|"));
        }
      }
    });
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

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Option
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  protected class OptionProperty extends Property<RadViewComponent> {
    private final PropertyRenderer myRenderer = new BooleanRenderer();
    private final PropertyEditor myEditor = new BooleanEditor();
    private final String myValueName;

    public OptionProperty(@Nullable Property parent, @NotNull String name, @NotNull String valueName) {
      super(parent, name);
      myValueName = valueName;
    }

    public String getValueName() {
      return myValueName;
    }

    @Override
    public Property<RadViewComponent> createForNewPresentation(@Nullable Property parent, @NotNull String name) {
      return null;
    }

    @Override
    public Object getValue(@NotNull RadViewComponent component) throws Exception {
      return isOption(component, myValueName);
    }

    @Override
    public void setValue(@NotNull RadViewComponent component, Object value) throws Exception {
      setOption(component, myValueName, (Boolean)value);
    }

    @Override
    public boolean isDefaultValue(@NotNull RadViewComponent component) throws Exception {
      return !isOption(component, myValueName);
    }

    @Override
    public void setDefaultValue(@NotNull RadViewComponent component) throws Exception {
      setValue(component, Boolean.FALSE);
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
    public boolean needRefreshPropertyList() {
      return true;
    }
  }
}