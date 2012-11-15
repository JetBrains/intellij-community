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
package com.intellij.android.designer.model;

import com.intellij.android.designer.propertyTable.CustomViewProperty;
import com.intellij.android.designer.propertyTable.editors.ChooseClassDialog;
import com.intellij.designer.ModuleProvider;
import com.intellij.designer.componentTree.AttributeWrapper;
import com.intellij.designer.model.*;
import com.intellij.designer.propertyTable.PropertyTable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Alexander Lobas
 */
public class RadCustomViewComponent extends RadViewComponent implements IConfigurableComponent, IComponentDecorator {
  private static final String NAME_KEY = "view.name";
  public static final String MODEL_KEY = "view.model";
  private static final Property CLASS_PROPERTY = new CustomViewProperty();

  @Override
  public String getCreationXml() {
    return "<view android:layout_width=\"wrap_content\"\n" +
           "android:layout_height=\"wrap_content\"\n" +
           "class=\"" +
           extractClientProperty(NAME_KEY) +
           "\"/>";
  }

  @Override
  public void configure(RadComponent rootComponent) throws Exception {
    String view = chooseView(rootComponent);
    if (view != null) {
      setClientProperty(NAME_KEY, view);
    }
    else {
      throw new Exception();
    }
  }

  @Nullable
  public static String chooseView(RadComponent rootComponent) {
    ModuleProvider moduleProvider = rootComponent.getClientProperty(ModelParser.MODULE_KEY);
    ChooseClassDialog dialog = new ChooseClassDialog(moduleProvider.getModule(), "Views", false) {
      @Override
      protected void findClasses(Module module, boolean includeAll, DefaultListModel model, String[] classes) {
        Set<String> names = new HashSet<String>();
        for (PsiClass psiClass : findInheritors(module, "android.view.View", false)) {
          model.addElement(psiClass);
          names.add(psiClass.getQualifiedName());
        }
        for (PsiClass psiClass : findInheritors(module, "android.view.View", true)) {
          String name = psiClass.getQualifiedName();
          if (!names.contains(name) && name != null && (!name.startsWith("android.") || name.startsWith("android.support"))) {
            model.addElement(psiClass);
          }
        }
      }
    };
    dialog.show();

    if (dialog.isOK()) {
      return dialog.getClassName();
    }

    return null;
  }

  @Nullable
  public String getViewClass() {
    XmlTag tag = getTag();

    String classAttribute = tag.getAttributeValue("class");
    if (!StringUtil.isEmpty(classAttribute)) {
      return classAttribute;
    }

    String tagName = tag.getName();
    if (!StringUtil.isEmpty(tagName) && !tagName.equals("view")) {
      return tagName;
    }

    return null;
  }

  @Override
  public void decorateTree(SimpleColoredComponent renderer, AttributeWrapper wrapper) {
    String viewClass = getViewClass();
    if (viewClass != null) {
      renderer.append(" - " + viewClass, wrapper.getAttribute(SimpleTextAttributes.REGULAR_ATTRIBUTES));
    }
  }

  @Override
  public MetaModel getMetaModelForProperties() throws Exception {
    MetaModel metaModel = getClientProperty(MODEL_KEY);

    if (metaModel == null) {
      ModuleProvider moduleProvider = getRoot().getClientProperty(ModelParser.MODULE_KEY);
      MetaManager metaManager = ViewsMetaManager.getInstance(moduleProvider.getProject());
      PsiClass viewClass = ChooseClassDialog.findClass(moduleProvider.getModule(), getViewClass());

      while (viewClass != null) {
        metaModel = metaManager.getModelByTarget(viewClass.getQualifiedName());
        if (metaModel != null) {
          break;
        }
        viewClass = viewClass.getSuperClass();
      }
      if (metaModel == null) {
        metaModel = myMetaModel;
      }

      setClientProperty(MODEL_KEY, metaModel);
    }

    return metaModel;
  }

  @Override
  public List<Property> getInplaceProperties() throws Exception {
    List<Property> properties = new ArrayList<Property>();
    MetaModel metaModel = getMetaModelForProperties();
    List<Property> allProperties = getProperties();

    properties.add(CLASS_PROPERTY);

    for (String name : metaModel.getInplaceProperties()) {
      Property property = PropertyTable.findProperty(allProperties, name);
      if (property != null) {
        properties.add(property);
      }
    }

    properties.add(PropertyTable.findProperty(allProperties, "id"));

    return properties;
  }

  @Override
  public void setProperties(List<Property> properties) {
    List<Property> oldProperties = getProperties();
    if (!properties.isEmpty() && (oldProperties == null || oldProperties.isEmpty())) {
      properties = new ArrayList<Property>(properties);
      properties.add(0, CLASS_PROPERTY);
    }
    super.setProperties(properties);
  }
}