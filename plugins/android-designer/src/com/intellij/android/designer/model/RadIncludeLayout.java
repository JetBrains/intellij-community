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


import com.android.SdkConstants;
import com.android.ide.common.resources.configuration.Configurable;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.intellij.android.designer.propertyTable.IdProperty;
import com.intellij.android.designer.propertyTable.IncludeLayoutProperty;
import com.intellij.android.designer.propertyTable.editors.ResourceDialog;
import com.intellij.designer.ModuleProvider;
import com.intellij.designer.model.Property;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.dom.converters.AndroidResourceReferenceBase;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RadIncludeLayout extends RadViewComponent implements IConfigurableComponent {
  private int myViewInfoCount = -1;
  private FolderConfiguration myConfiguration;

  @Override
  public String getCreationXml() {
    return "<include android:layout_width=\"wrap_content\"\n" +
           "android:layout_height=\"wrap_content\"\n" +
           "layout=\"" +
           extractClientProperty(IncludeLayoutProperty.NAME) +
           "\"/>";
  }

  public void configure(RadComponent rootComponent) throws Exception {
    ModuleProvider moduleProvider = rootComponent.getClientProperty(ModelParser.MODULE_KEY);
    ResourceDialog dialog = new ResourceDialog(moduleProvider.getModule(), IncludeLayoutProperty.TYPES, null, null) {
      @Override
      protected Action[] createLeftSideActions() {
        return new Action[0];
      }
    };
    dialog.show();

    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      setClientProperty(IncludeLayoutProperty.NAME, dialog.getResourceName());
    }
    else {
      throw new Exception();
    }
  }

  @Override
  public void setProperties(List<Property> properties) {
    if (!properties.isEmpty()) {
      properties = new ArrayList<Property>(properties);
      properties.add(IncludeLayoutProperty.INSTANCE);
      properties.add(IdProperty.INSTANCE);
    }
    super.setProperties(properties);
  }

  public void clearViewInfoCount() {
    myViewInfoCount = -1;
  }

  @Override
  public int getViewInfoCount() {
    FolderConfiguration configuration = getRoot().getClientProperty(ModelParser.FOLDER_CONFIG_KEY);

    if (myViewInfoCount != -1 && !configuration.isMatchFor(myConfiguration)) {
      myViewInfoCount = -1;
    }

    if (myViewInfoCount == -1) {
      myConfiguration = configuration;

      XmlAttribute layout = getTag().getAttribute("layout");
      XmlAttributeValue value = layout == null ? null : layout.getValueElement();
      if (value == null) {
        myViewInfoCount = 0;
      }
      else {
        PsiReference reference = value.getReference();

        if (reference == null) {
          myViewInfoCount = 0;
        }
        else {
          XmlFile xmlFile = findFile(configuration, (AndroidResourceReferenceBase)reference);
          XmlTag tag = xmlFile == null ? null : xmlFile.getRootTag();

          if (tag == null) {
            myViewInfoCount = 0;
          }
          else if ("merge".equalsIgnoreCase(tag.getName())) {
            myViewInfoCount = tag.getSubTags().length;
          }
          else {
            myViewInfoCount = 1;
          }
        }
      }
    }
    return myViewInfoCount;
  }

  @Nullable
  private static XmlFile findFile(FolderConfiguration configuration, AndroidResourceReferenceBase reference) {
    PsiElement[] elements = reference.computeTargetElements();

    if (elements.length == 0) {
      return null;
    }
    if (elements.length == 1) {
      return (XmlFile)elements[0];
    }

    List<MyConfigurable> configurables = new ArrayList<MyConfigurable>();
    for (PsiElement element : elements) {
      XmlFile xmlFile = (XmlFile)element;

      VirtualFile file = xmlFile.getVirtualFile();
      if (file == null) {
        continue;
      }
      VirtualFile folder = file.getParent();
      if (folder == null) {
        continue;
      }

      FolderConfiguration includeConfig = FolderConfiguration.getConfig(folder.getName().split(SdkConstants.RES_QUALIFIER_SEP));
      configurables.add(new MyConfigurable(xmlFile, includeConfig));
    }

    MyConfigurable configurable = (MyConfigurable)configuration.findMatchingConfigurable(configurables);
    return configurable == null ? null : configurable.getFile();
  }

  private static class MyConfigurable implements Configurable {
    private final XmlFile myFile;
    private final FolderConfiguration myConfig;

    private MyConfigurable(XmlFile file, FolderConfiguration config) {
      myFile = file;
      myConfig = config;
    }

    @Override
    public FolderConfiguration getConfiguration() {
      return myConfig;
    }

    public XmlFile getFile() {
      return myFile;
    }
  }
}