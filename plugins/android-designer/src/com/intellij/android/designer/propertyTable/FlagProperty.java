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
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.propertyTable.Property;
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
public class FlagProperty extends Property<RadViewComponent> {
  private final PropertyRenderer myRenderer = new LabelPropertyRenderer(null);
  private final AttributeDefinition myDefinition;
  private final List<Property> myOptions = new ArrayList<Property>();

  public FlagProperty(@NotNull String name, @NotNull AttributeDefinition definition, @Nullable MetaModel model) {
    super(null, name);
    myDefinition = definition;

    for (String option : definition.getValues()) {
      myOptions.add(new OptionProperty(this, option, option));
    }

    if (model != null) {
      for (Property option : myOptions) {
        String optionName = name + "." + option.getName();
        option.setImportant(model.isImportantProperty(optionName));
        option.setExpert(model.isExpertProperty(optionName));
        option.setDeprecated(model.isDeprecatedProperty(optionName));
      }
    }
  }

  @Override
  public Property createForNewPresentation() {
    return new FlagProperty(getName(), myDefinition, null);
  }

  @Override
  public List<Property> getChildren(@Nullable RadViewComponent component) {
    return myOptions;
  }

  @Override
  public Object getValue(RadViewComponent component) throws Exception {
    StringBuffer value = new StringBuffer("[");
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
  public boolean isDefaultValue(RadViewComponent component) throws Exception {
    return getAttribute(component) == null;
  }

  @Override
  public void setDefaultValue(RadViewComponent component) throws Exception {
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
  private XmlAttribute getAttribute(RadViewComponent component) {
    return component.getTag().getAttribute("android:" + myDefinition.getName());
  }

  private Set<String> getOptions(RadViewComponent component) throws Exception {
    String value = component.getTag().getAttributeValue("android:" + myDefinition.getName());
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
          component.getTag().setAttribute("android:" + myDefinition.getName(), StringUtil.join(options, "|"));
        }
      }
    });
  }

  private class OptionProperty extends Property<RadViewComponent> {
    private PropertyRenderer myRenderer = new BooleanRenderer();
    private PropertyEditor myEditor = new BooleanEditor();
    private final String myValueName;

    public OptionProperty(@Nullable Property parent, @NotNull String name, @NotNull String valueName) {
      super(parent, name);
      myValueName = valueName;
    }

    public String getValueName() {
      return myValueName;
    }

    @Override
    public Property createForNewPresentation() {
      return null;
    }

    @Override
    public Object getValue(RadViewComponent component) throws Exception {
      return isOption(component, myValueName);
    }

    @Override
    public void setValue(RadViewComponent component, Object value) throws Exception {
      setOption(component, myValueName, (Boolean)value);
    }

    @Override
    public boolean isDefaultValue(RadViewComponent component) throws Exception {
      return !isOption(component, myValueName);
    }

    @Override
    public void setDefaultValue(RadViewComponent component) throws Exception {
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