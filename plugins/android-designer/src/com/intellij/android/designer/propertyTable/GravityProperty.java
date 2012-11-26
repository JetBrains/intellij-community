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
import com.intellij.android.designer.propertyTable.editors.StringsComboEditor;
import com.intellij.designer.model.Property;
import com.intellij.designer.propertyTable.PropertyEditor;
import com.intellij.designer.propertyTable.PropertyRenderer;
import com.intellij.designer.propertyTable.renderers.LabelPropertyRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Alexander Lobas
 */
public class GravityProperty extends FlagProperty {
  private static final String[] CENTER = {"center_horizontal", "center_vertical", "center"};
  private static final String[] FILL = {"fill_horizontal", "fill_vertical", "fill"};
  private static final String[] CLIP = {"clip_horizontal", "clip_vertical"};

  private static final String[] COMBO_ITEMS = {"horizontal", "vertical", "both"};

  public GravityProperty(@NotNull String name, @NotNull AttributeDefinition definition) {
    super(name, definition);

    myOptions.remove(getProperty("center_horizontal"));
    myOptions.remove(getProperty("center_vertical"));
    myOptions.set(getProperty("center"), new ComboOptionProperty(this, "center", CENTER) {
      @Override
      protected void setValue(RadViewComponent component, int index) throws Exception {
        if (index == 0) {
          setOptions(component, new String[]{"center_horizontal"}, new String[]{"center_vertical", "center"});
        }
        else if (index == 1) {
          setOptions(component, new String[]{"center_vertical"}, new String[]{"center_horizontal", "center"});
        }
        else {
          setOptions(component, new String[]{"center"}, new String[]{"center_horizontal", "center_vertical"});
        }
      }
    });

    myOptions.remove(getProperty("fill_horizontal"));
    myOptions.remove(getProperty("fill_vertical"));
    myOptions.set(getProperty("fill"), new ComboOptionProperty(this, "fill", FILL) {
      @Override
      protected void setValue(RadViewComponent component, int index) throws Exception {
        if (index == 0) {
          setOptions(component, new String[]{"fill_horizontal"}, new String[]{"fill_vertical", "fill"});
        }
        else if (index == 1) {
          setOptions(component, new String[]{"fill_vertical"}, new String[]{"fill_horizontal", "fill"});
        }
        else {
          setOptions(component, new String[]{"fill"}, new String[]{"fill_horizontal", "fill_vertical"});
        }
      }
    });

    int clip_vertical = getProperty("clip_vertical");
    if (clip_vertical != -1) {
      myOptions.remove(clip_vertical);
      myOptions.set(getProperty("clip_horizontal"), new ComboOptionProperty(this, "clip", CLIP) {
        @Override
        protected void setValue(RadViewComponent component, int index) throws Exception {
          if (index == 0) {
            setOptions(component, new String[]{"clip_horizontal"}, new String[]{"clip_vertical"});
          }
          else if (index == 1) {
            setOptions(component, new String[]{"clip_vertical"}, new String[]{"clip_horizontal"});
          }
          else {
            setOptions(component, CLIP, null);
          }
        }
      });
    }
  }

  private int getProperty(String name) {
    int size = myOptions.size();
    for (int i = 0; i < size; i++) {
      if (name.equals(myOptions.get(i).getName())) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public Object getValue(@NotNull RadViewComponent component) throws Exception {
    StringBuilder value = new StringBuilder("[");
    Set<String> options = getOptions(component);
    int index = 0;
    for (Property option : myOptions) {
      String name = null;

      if (option instanceof OptionProperty) {
        if (options.contains(((OptionProperty)option).getValueName())) {
          name = option.getName();
        }
      }
      else {
        name = ((ComboOptionProperty)option).getValue(options);
      }

      if (name != null) {
        if (index++ > 0) {
          value.append(", ");
        }
        value.append(name);
      }
    }
    return value.append("]").toString();
  }

  private void setOptions(final RadViewComponent component, @Nullable String[] setNames, @Nullable String[] unsetNames) throws Exception {
    final Set<String> options = new HashSet<String>(getOptions(component));
    if (unsetNames != null) {
      for (String name : unsetNames) {
        options.remove(name);
      }
    }
    if (setNames != null) {
      Collections.addAll(options, setNames);
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

  private abstract class ComboOptionProperty extends Property<RadViewComponent> {
    private final PropertyRenderer myRenderer = new LabelPropertyRenderer(null);
    private final PropertyEditor myEditor = new StringsComboEditor(COMBO_ITEMS);
    private final String[] myValues;

    public ComboOptionProperty(@Nullable Property parent, @NotNull String name, String[] values) {
      super(parent, name);
      myValues = values;
    }

    @Nullable
    public String getValue(Set<String> options) {
      StringBuilder values = new StringBuilder();
      int index = 0;
      for (String value : myValues) {
        if (options.contains(value)) {
          if (index++ > 0) {
            values.append(", ");
          }
          values.append(value);
        }
      }
      return index == 0 ? null : values.toString();
    }

    @Override
    public Object getValue(@NotNull RadViewComponent component) throws Exception {
      Set<String> options = getOptions(component);
      int lastIndex = -1;
      for (int i = 0; i < myValues.length; i++) {
        if (options.contains(myValues[i])) {
          lastIndex = i;
        }
      }
      return lastIndex == -1 ? null : COMBO_ITEMS[lastIndex];
    }

    @Override
    public void setValue(@NotNull RadViewComponent component, Object value) throws Exception {
      int index = ArrayUtil.indexOf(COMBO_ITEMS, value);
      if (index == -1) {
        setOptions(component, null, myValues);
      }
      else {
        setValue(component, index);
      }
    }

    protected abstract void setValue(RadViewComponent component, int index) throws Exception;

    @Override
    public boolean isDefaultValue(@NotNull RadViewComponent component) throws Exception {
      Set<String> options = getOptions(component);
      for (String value : myValues) {
        if (options.contains(value)) {
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
    public boolean needRefreshPropertyList() {
      return true;
    }
  }
}