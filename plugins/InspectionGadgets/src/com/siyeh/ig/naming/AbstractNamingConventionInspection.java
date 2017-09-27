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
package com.siyeh.ig.naming;

import com.intellij.codeInspection.NamingConvention;
import com.intellij.codeInspection.NamingConventionBean;
import com.intellij.codeInspection.NamingConventionWithFallbackBean;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.CheckBoxListListener;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.xmlb.XmlSerializationException;
import com.intellij.util.xmlb.XmlSerializer;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

public abstract class AbstractNamingConventionInspection<T> extends BaseInspection {
  private static final Logger LOG = Logger.getInstance(AbstractNamingConventionInspection.class);

  private final Map<String, NamingConvention<T>> myNamingConventions = new LinkedHashMap<>();
  private final Map<String, NamingConventionBean> myNamingConventionBeans = new LinkedHashMap<>();
  private final Set<String> myDisabledShortNames = new HashSet<>();
  private final String myDefaultConventionShortName;

  public AbstractNamingConventionInspection(NamingConvention<T>[] extensions, final String defaultConventionShortName) {
    for (NamingConvention<T> convention : extensions) {
      String shortName = convention.getShortName();
      NamingConvention<T> oldConvention = myNamingConventions.put(shortName, convention);
      if (oldConvention != null) {
        LOG.error("Duplicated short names: " + shortName + " first: " + oldConvention + "; second: " + convention);
      }
      myNamingConventionBeans.put(shortName, convention.createDefaultBean());
    }
    initDisabledState();
    myDefaultConventionShortName = defaultConventionShortName;
  }

  private void initDisabledState() {
    myDisabledShortNames.clear();
    myDisabledShortNames.addAll(myNamingConventions.keySet());
  }

  public NamingConventionBean getNamingConventionBean(String shortName) {
    return myNamingConventionBeans.get(shortName);
  }

  public Set<String> getOldToolNames() {
    return myNamingConventions.keySet();
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final String name = (String)infos[0];
    final String shortName = (String)infos[1];
    return myNamingConventions.get(shortName).createErrorMessage(name, myNamingConventionBeans.get(shortName));
  }

  @Override
  public void readSettings(@NotNull Element node) {
    initDisabledState();
    for (Element extension : node.getChildren("extension")) {
      String shortName = extension.getAttributeValue("name");
      if (shortName == null) continue;
      NamingConventionBean conventionBean = myNamingConventionBeans.get(shortName);
      try {
        XmlSerializer.deserializeInto(conventionBean, extension);
        conventionBean.initPattern();
      }
      catch (XmlSerializationException e) {
        throw new InvalidDataException(e);
      }
      String enabled = extension.getAttributeValue("enabled");
      if (Boolean.parseBoolean(enabled)) {
        myDisabledShortNames.remove(shortName);
      }
    }
  }

  @Override
  public void writeSettings(@NotNull Element node) {
    for (NamingConvention<T> convention : myNamingConventions.values()) {
      String shortName = convention.getShortName();
      boolean disabled = myDisabledShortNames.contains(shortName);
      Element element = new Element("extension")
        .setAttribute("name", shortName)
        .setAttribute("enabled", disabled ? "false" : "true");
      NamingConventionBean conventionBean = myNamingConventionBeans.get(shortName);
      if (!convention.createDefaultBean().equals(conventionBean)) {
        XmlSerializer.serializeInto(conventionBean, element);
      }
      else {
        if (disabled) continue;
      }
      node.addContent(element);
    }
  }

  public boolean isConventionEnabled(String shortName) {
    return !myDisabledShortNames.contains(shortName);
  }

  protected void checkName(T member, String name, Consumer<String> errorRegister) {
    for (NamingConvention<T> namingConvention : myNamingConventions.values()) {
      if (namingConvention.isApplicable(member)) {
        String shortName = namingConvention.getShortName();
        if (myDisabledShortNames.contains(shortName)) {
          break;
        }
        NamingConventionBean activeBean = myNamingConventionBeans.get(shortName);
        if (activeBean instanceof NamingConventionWithFallbackBean && ((NamingConventionWithFallbackBean)activeBean).isInheritDefaultSettings()) {
          //disabled when fallback is disabled
          if (myDisabledShortNames.contains(myDefaultConventionShortName)) {
            break;
          }
          activeBean = myNamingConventionBeans.get(myDefaultConventionShortName);
        }
        if (!activeBean.isValid(name)) {
          errorRegister.accept(shortName);
        }
        break;
      }
    }
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    JPanel panel = new JPanel(new BorderLayout(JBUI.scale(2), JBUI.scale(2)));
    CardLayout layout = new CardLayout();
    JPanel descriptionPanel = new JPanel(layout);
    descriptionPanel.setBorder(JBUI.Borders.empty(2));
    panel.add(descriptionPanel, BorderLayout.CENTER);
    CheckBoxList<NamingConvention<T>> list = new CheckBoxList<>();
    list.setBorder(JBUI.Borders.empty(2));
    List<NamingConvention<T>> values = new ArrayList<>(myNamingConventions.values());
    Collections.reverse(values);
    for (NamingConvention<T> convention : values) {
      String shortName = convention.getShortName();
      list.addItem(convention, convention.getElementDescription(), !myDisabledShortNames.contains(shortName));
      descriptionPanel.add(myNamingConventionBeans.get(shortName).createOptionsPanel(), shortName);
    }
    list.addListSelectionListener((e) -> {
      int selectedIndex = list.getSelectedIndex();
      NamingConvention<T> item = list.getItemAt(selectedIndex);
      if (item != null) {
        String shortName = item.getShortName();
        layout.show(descriptionPanel, shortName);
        UIUtil.setEnabled(descriptionPanel, list.isItemSelected(selectedIndex), true);
      }
    });
    list.setCheckBoxListListener(new CheckBoxListListener() {
      @Override
      public void checkBoxSelectionChanged(int index, boolean value) {
        NamingConvention<T> convention = new ArrayList<>(myNamingConventions.values()).get(index);
        setEnabled(value, convention.getShortName());
        UIUtil.setEnabled(descriptionPanel, value, true);
      }
    });
    list.setSelectedIndex(0);
    panel.add(new JBScrollPane(list), BorderLayout.WEST);
    return panel;
  }

  public void setEnabled(boolean value, String conventionShortName) {
    if (value) {
      myDisabledShortNames.remove(conventionShortName);
    }
    else {
      myDisabledShortNames.add(conventionShortName);
    }
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new RenameFix();
  }
}
