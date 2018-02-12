/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeStyle;

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.psi.codeStyle.CodeStyleConfigurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class GroovyCodeStyleGenerationConfigurable implements CodeStyleConfigurable {
  private final CodeStyleSettings mySettings;
  private final MembersOrderList myMembersOrderList;

  public GroovyCodeStyleGenerationConfigurable(CodeStyleSettings settings) {
    mySettings = settings;
    myMembersOrderList = new MembersOrderList();
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    JPanel panel = ToolbarDecorator.createDecorator(myMembersOrderList)
      .disableAddAction().disableRemoveAction().createPanel();

    JPanel wholePanel = new JPanel(new BorderLayout());
    wholePanel.setBorder(IdeBorderFactory.createTitledBorder(ApplicationBundle.message("title.order.of.members"), true, new JBInsets(0, 10, 10, 10)));
    wholePanel.add(panel, BorderLayout.NORTH);
    return wholePanel;
  }

  @Override
  public boolean isModified() {
    return myMembersOrderList.isModified(mySettings);
  }

  @Override
  public void apply() throws ConfigurationException {
    apply(mySettings);
  }

  @Override
  public void reset() {
    reset(mySettings);
  }

  @Nls
  @Override
  public String getDisplayName() {
    return ApplicationBundle.message("title.code.generation");
  }

  @Override
  public void reset(@NotNull CodeStyleSettings settings) {
    myMembersOrderList.reset(settings);
  }

  @Override
  public void apply(@NotNull CodeStyleSettings settings) throws ConfigurationException {
    myMembersOrderList.apply(settings);
  }

  public static class MembersOrderList extends JBList {

    private static abstract class PropertyManager {

      public final String myName;

      protected PropertyManager(String nameKey) {
        myName = ApplicationBundle.message(nameKey);
      }

      abstract void apply(CodeStyleSettings settings, int value);
      abstract int getValue(CodeStyleSettings settings);
    }

    private static final Map<String, PropertyManager> PROPERTIES = new HashMap<>();
    static {
      init();
    }

    private final DefaultListModel myModel;

    public MembersOrderList() {
      myModel = new DefaultListModel();
      setModel(myModel);
      setVisibleRowCount(PROPERTIES.size());
    }

    public void reset(final CodeStyleSettings settings) {
      myModel.removeAllElements();
      for (String string : getPropertyNames(settings)) {
        myModel.addElement(string);
      }

      setSelectedIndex(0);
    }

    private static void init() {
      PropertyManager staticFieldManager = new PropertyManager("listbox.members.order.static.fields") {
        @Override void apply(CodeStyleSettings settings, int value) {
          settings.getCustomSettings(GroovyCodeStyleSettings.class).STATIC_FIELDS_ORDER_WEIGHT = value;
        }
        @Override int getValue(CodeStyleSettings settings) {
          return settings.getCustomSettings(GroovyCodeStyleSettings.class).STATIC_FIELDS_ORDER_WEIGHT;
        }
      };
      PROPERTIES.put(staticFieldManager.myName, staticFieldManager);

      PropertyManager instanceFieldManager = new PropertyManager("listbox.members.order.fields") {
        @Override void apply(CodeStyleSettings settings, int value) {
          settings.getCustomSettings(GroovyCodeStyleSettings.class).FIELDS_ORDER_WEIGHT = value;
        }
        @Override int getValue(CodeStyleSettings settings) {
          return settings.getCustomSettings(GroovyCodeStyleSettings.class).FIELDS_ORDER_WEIGHT;
        }
      };
      PROPERTIES.put(instanceFieldManager.myName, instanceFieldManager);

      PropertyManager constructorManager = new PropertyManager("listbox.members.order.constructors") {
        @Override void apply(CodeStyleSettings settings, int value) {
          settings.getCustomSettings(GroovyCodeStyleSettings.class).CONSTRUCTORS_ORDER_WEIGHT = value;
        }
        @Override int getValue(CodeStyleSettings settings) {
          return settings.getCustomSettings(GroovyCodeStyleSettings.class).CONSTRUCTORS_ORDER_WEIGHT;
        }
      };
      PROPERTIES.put(constructorManager.myName, constructorManager);

      PropertyManager staticMethodManager = new PropertyManager("listbox.members.order.static.methods") {
        @Override void apply(CodeStyleSettings settings, int value) {
          settings.getCustomSettings(GroovyCodeStyleSettings.class).STATIC_METHODS_ORDER_WEIGHT = value;
        }
        @Override int getValue(CodeStyleSettings settings) {
          return settings.getCustomSettings(GroovyCodeStyleSettings.class).STATIC_METHODS_ORDER_WEIGHT;
        }
      };
      PROPERTIES.put(staticMethodManager.myName, staticMethodManager);

      PropertyManager instanceMethodManager = new PropertyManager("listbox.members.order.methods") {
        @Override void apply(CodeStyleSettings settings, int value) {
          settings.getCustomSettings(GroovyCodeStyleSettings.class).METHODS_ORDER_WEIGHT = value;
        }
        @Override int getValue(CodeStyleSettings settings) {
          return settings.getCustomSettings(GroovyCodeStyleSettings.class).METHODS_ORDER_WEIGHT;
        }
      };
      PROPERTIES.put(instanceMethodManager.myName, instanceMethodManager);

      PropertyManager staticInnerClassManager = new PropertyManager("listbox.members.order.inner.static.classes") {
        @Override void apply(CodeStyleSettings settings, int value) {
          settings.getCustomSettings(GroovyCodeStyleSettings.class).STATIC_INNER_CLASSES_ORDER_WEIGHT = value;
        }
        @Override int getValue(CodeStyleSettings settings) {
          return settings.getCustomSettings(GroovyCodeStyleSettings.class).STATIC_INNER_CLASSES_ORDER_WEIGHT;
        }
      };
      PROPERTIES.put(staticInnerClassManager.myName, staticInnerClassManager);

      PropertyManager innerClassManager = new PropertyManager("listbox.members.order.inner.classes") {
        @Override void apply(CodeStyleSettings settings, int value) {
          settings.getCustomSettings(GroovyCodeStyleSettings.class).INNER_CLASSES_ORDER_WEIGHT = value;
        }
        @Override int getValue(CodeStyleSettings settings) {
          return settings.getCustomSettings(GroovyCodeStyleSettings.class).INNER_CLASSES_ORDER_WEIGHT;
        }
      };
      PROPERTIES.put(innerClassManager.myName, innerClassManager);
    }

    private static Iterable<String> getPropertyNames(final CodeStyleSettings settings) {
      List<String> result = new ArrayList<>(PROPERTIES.keySet());
      Collections.sort(result, new Comparator<String>() {
        public int compare(String o1, String o2) {
          int weight1 = getWeight(o1);
          int weight2 = getWeight(o2);
          return weight1 - weight2;
        }

        private int getWeight(String o) {
          PropertyManager propertyManager = PROPERTIES.get(o);
          if (propertyManager == null) {
            throw new IllegalArgumentException("unexpected " + o);
          }
          return propertyManager.getValue(settings);
        }
      });
      return result;
    }

    public void apply(CodeStyleSettings settings) {
      for (int i = 0; i < myModel.size(); i++) {
        Object o = myModel.getElementAt(i);
        if (o == null) {
          throw new IllegalArgumentException("unexpected " + o);
        }
        PropertyManager propertyManager = PROPERTIES.get(o.toString());
        if (propertyManager == null) {
          throw new IllegalArgumentException("unexpected " + o);
        }
        propertyManager.apply(settings, i + 1);
      }
    }

    public boolean isModified(CodeStyleSettings settings) {
      Iterable<String> oldProperties = getPropertyNames(settings);
      int i = 0;
      for (String property : oldProperties) {
        if (i >= myModel.size() || !property.equals(myModel.getElementAt(i))) {
          return true;
        }
        i++;
      }
      return false;
    }
  }
}
