// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeStyle;

import com.intellij.application.options.codeStyle.CommenterForm;
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
import org.jetbrains.plugins.groovy.GroovyLanguage;

import javax.swing.*;
import java.util.*;

public class GroovyCodeStyleGenerationConfigurable implements CodeStyleConfigurable {
  private final CodeStyleSettings mySettings;
  private final MembersOrderList myMembersOrderList;
  private final CommenterForm myCommenterForm;

  public GroovyCodeStyleGenerationConfigurable(CodeStyleSettings settings) {
    mySettings = settings;
    myMembersOrderList = new MembersOrderList();
    myCommenterForm = new CommenterForm(GroovyLanguage.INSTANCE);
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    JPanel membersOrderPanel = ToolbarDecorator.createDecorator(myMembersOrderList).disableAddAction().disableRemoveAction().createPanel();
    membersOrderPanel.setBorder(IdeBorderFactory.createTitledBorder(ApplicationBundle.message("title.order.of.members")));

    JPanel wholePanel = new JPanel();
    wholePanel.setLayout(new BoxLayout(wholePanel, BoxLayout.Y_AXIS));
    wholePanel.setBorder(IdeBorderFactory.createEmptyBorder(new JBInsets(0, 10, 10, 10)));
    wholePanel.add(membersOrderPanel);
    wholePanel.add(myCommenterForm.getCommenterPanel());
    return wholePanel;
  }

  @Override
  public boolean isModified() {
    return myMembersOrderList.isModified(mySettings) || myCommenterForm.isModified(mySettings);
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
    myCommenterForm.reset(settings);
  }

  @Override
  public void apply(@NotNull CodeStyleSettings settings) throws ConfigurationException {
    myMembersOrderList.apply(settings);
    myCommenterForm.apply(settings);
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
