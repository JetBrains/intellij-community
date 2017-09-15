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

/*
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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.CheckBoxListListener;
import com.intellij.util.ui.JBUI;
import com.intellij.util.xmlb.XmlSerializationException;
import com.intellij.util.xmlb.XmlSerializer;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;

public class NewClassNamingConventionInspection extends BaseInspection {
  public static final ExtensionPointName<NamingConvention<PsiClass>> EP_NAME = ExtensionPointName.create("com.intellij.naming.convention.class");


  private final Map<String, NamingConvention<PsiClass>> myNamingConventions = new LinkedHashMap<>();
  private final Map<String, NamingConventionBean> myNamingConventionBeans = new LinkedHashMap<>();
  private final Set<String> myDisabledShortNames = new HashSet<>();

  public NewClassNamingConventionInspection() {
    for (NamingConvention<PsiClass> convention : EP_NAME.getExtensions()) {
      myNamingConventions.put(convention.getShortName(), convention);
      myNamingConventionBeans.put(convention.getShortName(), convention.createDefaultBean());
    }
    initDisabledState();
  }

  private void initDisabledState() {
    myDisabledShortNames.clear();
    myDisabledShortNames.addAll(myNamingConventions.keySet());
  }

  public NamingConventionBean getNamingConventionBean(String shortName) {
    return myNamingConventionBeans.get(shortName);
  }
  
  @Override
  public boolean shouldInspect(PsiFile file) {
    return file instanceof PsiClassOwner;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "class.naming.convention.display.name");
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
    for (NamingConvention<PsiClass> convention : myNamingConventions.values()) {
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

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NamingConventionsVisitor();
  }

  public boolean isConventionEnabled(String shortName) {
    return !myDisabledShortNames.contains(shortName);
  }

  private class NamingConventionsVisitor extends BaseInspectionVisitor {
    @Override
    public void visitElement(PsiElement element) {
      if (element instanceof PsiClass) {
        PsiClass aClass = (PsiClass)element;
        final String name = aClass.getName();
        if (name == null) return;
        for (NamingConvention<PsiClass> namingConvention : myNamingConventions.values()) {
          if (namingConvention.isApplicable(aClass)) {
            String shortName = namingConvention.getShortName();
            if (myDisabledShortNames.contains(shortName)) {
              break;
            }
            NamingConventionBean activeBean = myNamingConventionBeans.get(shortName);
            if (activeBean instanceof NamingConventionWithFallbackBean && ((NamingConventionWithFallbackBean)activeBean).isInheritDefaultSettings()) {
              //disabled when fallback is disabled
              if (myDisabledShortNames.contains(ClassNamingConvention.CLASS_NAMING_CONVENTION_SHORT_NAME)) {
                break;
              }
              activeBean = myNamingConventionBeans.get(ClassNamingConvention.CLASS_NAMING_CONVENTION_SHORT_NAME);
            }
            if (!activeBean.isValid(name)) {
              registerClassError(aClass, name, shortName);
            }
            break;
          }
        }
      }
    }
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    JPanel descriptionPanel = new JPanel(new BorderLayout());
    descriptionPanel.setBorder(JBUI.Borders.empty(2));
    panel.add(descriptionPanel, BorderLayout.CENTER);
    CheckBoxList<NamingConvention<PsiClass>> list = new CheckBoxList<>();
    for (NamingConvention<PsiClass> convention : myNamingConventions.values()) {
      list.addItem(convention, convention.getElementDescription(), !myDisabledShortNames.contains(convention.getShortName()));
    }
    list.addListSelectionListener((e) -> {
      descriptionPanel.removeAll();
      int selectedIndex = list.getSelectedIndex();
      NamingConvention<PsiClass> item = list.getItemAt(selectedIndex);
      if (item != null) {
        descriptionPanel.add(myNamingConventionBeans.get(item.getShortName()).createOptionsPanel(), BorderLayout.CENTER);
      }
    });
    list.setCheckBoxListListener(new CheckBoxListListener() {
      @Override
      public void checkBoxSelectionChanged(int index, boolean value) {
        NamingConvention<PsiClass> convention = new ArrayList<>(myNamingConventions.values()).get(index);
        setEnabled(value, convention.getShortName());
      }
    });
    list.setSelectedIndex(myNamingConventions.size() - 1);
    panel.add(list, BorderLayout.WEST);
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