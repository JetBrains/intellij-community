/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.android.converter;

import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.ConversionProcessor;
import com.intellij.conversion.ModuleSettings;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.impl.OrderEntryFactory;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetType;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidSdkType;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidModuleConverter1 extends ConversionProcessor<ModuleSettings> {
  private static final String PLATFORM_NAME_ATTRIBUTE = "PLATFORM_NAME";
  private static final String NEW_MODULE_MANAGER = "NewModuleRootManager";
  @NonNls private static final String OPTION_VALUE_ATTRIBUTE = "value";

  @Override
  public boolean isConversionNeeded(ModuleSettings moduleSettings) {
    Element confElement = findAndroidFacetConfigurationElement(moduleSettings);
    return confElement != null && getOptionValue(confElement, PLATFORM_NAME_ATTRIBUTE) != null;
  }

  @Override
  public void process(ModuleSettings moduleSettings) throws CannotConvertException {
    Element confElement = findAndroidFacetConfigurationElement(moduleSettings);
    assert confElement != null;

    Element platformNameElement = getOptionElement(confElement, PLATFORM_NAME_ATTRIBUTE);
    String platformName = platformNameElement != null ? platformNameElement.getAttributeValue(OPTION_VALUE_ATTRIBUTE) : null;

    if (platformName == null) return;

    Library androidLibrary = LibraryTablesRegistrar.getInstance().getLibraryTable().getLibraryByName(platformName);
    if (androidLibrary != null) {

      AndroidPlatform androidPlatform = AndroidPlatform.parse(androidLibrary, null, null);

      if (androidPlatform != null) {
        Sdk androidSdk =
          AndroidSdkUtils.createNewAndroidPlatform(androidPlatform.getTarget(), androidPlatform.getSdk().getLocation(), false);

        SdkModificator modificator = androidSdk.getSdkModificator();

        for (OrderRootType type : OrderRootType.getAllTypes()) {
          for (VirtualFile root : androidLibrary.getFiles(type)) {
            modificator.addRoot(root, type);
          }
        }
        modificator.commitChanges();

        addNewDependency(moduleSettings, androidSdk.getName());
      }
    }

    removeOldDependency(moduleSettings, platformName);
    confElement.removeContent(platformNameElement);
  }

  @Nullable
  private static Element findAndroidFacetConfigurationElement(ModuleSettings moduleSettings) {
    if (moduleSettings != null) {
      AndroidFacetType facetType = AndroidFacet.getFacetType();
      if (facetType != null) {
        final Collection<? extends Element> facetElements = moduleSettings.getFacetElements(facetType.getStringId());
        if (!facetElements.isEmpty()) {
          return facetElements.iterator().next().getChild("configuration");
        }
      }
    }
    return null;
  }

  @Nullable
  private static String getOptionValue(@NotNull Element facetElement, @NotNull String optionName) {
    Element element = getOptionElement(facetElement, optionName);
    return element != null ? element.getAttributeValue(OPTION_VALUE_ATTRIBUTE) : null;
  }

  @Nullable
  private static Element getOptionElement(Element facetElement, String optionName) {
    Element e = null;
    for (Element optionElement : getChildren(facetElement, "option")) {
      if (optionName.equals(optionElement.getAttributeValue("name"))) {
        return optionElement;
      }
    }
    return e;
  }

  private static Element[] getChildren(@NotNull Element parent, @NotNull String name) {
    final List<?> children = parent.getChildren(name);
    return children.toArray(new Element[children.size()]);
  }

  private static void addNewDependency(ModuleSettings moduleSettings, @NotNull String jdkName) {
    Element moduleManagerElement = moduleSettings.getComponentElement(NEW_MODULE_MANAGER);
    if (moduleManagerElement != null) {
      Element newEntryElement = new Element(OrderEntryFactory.ORDER_ENTRY_ELEMENT_NAME);
      newEntryElement.setAttribute("type", "jdk");
      newEntryElement.setAttribute("jdkName", jdkName);
      newEntryElement.setAttribute("jdkType", AndroidSdkType.SDK_NAME);
      moduleManagerElement.addContent(newEntryElement);
    }
  }

  private static void removeOldDependency(ModuleSettings moduleSettings, @NotNull String libName) {
    Element moduleManagerElement = moduleSettings.getComponentElement(NEW_MODULE_MANAGER);
    if (moduleManagerElement != null) {
      for (Element entryElement : getChildren(moduleManagerElement, OrderEntryFactory.ORDER_ENTRY_ELEMENT_NAME)) {
        if (libName.equals(entryElement.getAttributeValue("name")) &&
            "library".equals(entryElement.getAttributeValue("type")) &&
            "application".equals(entryElement.getAttributeValue("level"))) {
          moduleManagerElement.removeContent(entryElement);
        }
      }
    }
  }
}
