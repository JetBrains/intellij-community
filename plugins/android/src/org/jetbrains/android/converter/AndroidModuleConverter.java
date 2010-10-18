/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.android.converter;

import com.android.sdklib.IAndroidTarget;
import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.ConversionProcessor;
import com.intellij.conversion.ModuleSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.impl.OrderEntryFactory;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetType;
import org.jetbrains.android.sdk.AndroidLibraryManager;
import org.jetbrains.android.sdk.AndroidSdk;
import org.jetbrains.android.sdk.EmptySdkLog;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Aug 19, 2009
 * Time: 4:45:16 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidModuleConverter extends ConversionProcessor<ModuleSettings> {
  private static final String SDK_PATH_ATTRIBUTE = "SDK_PATH";
  private static final String TARGET_NAME_ATTRIBUTE = "ANDROID_TARGET_NAME";
  private static final String PLATFORM_NAME_ATTRIBUTE = "PLATFORM_NAME";
  private static final String ANDROID_MODULE_LIBRARY_NAME = "Android SDK";
  private static final String NEW_MODULE_MANAGER = "NewModuleRootManager";

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
    for (Element optionElement : getChildren(facetElement, "option")) {
      if (optionName.equals(optionElement.getAttributeValue("name"))) {
        return optionElement.getAttributeValue("value");
      }
    }
    return null;
  }

  private static Element[] getChildren(@NotNull Element parent, @NotNull String name) {
    final List<?> children = parent.getChildren(name);
    return children.toArray(new Element[children.size()]);
  }

  @Override
  public boolean isConversionNeeded(ModuleSettings moduleSettings) {
    Element confElement = findAndroidFacetConfigurationElement(moduleSettings);
    return confElement != null && getOptionValue(confElement, SDK_PATH_ATTRIBUTE) != null;
  }

  private static void addOption(Element element, String name, String value) {
    Element optionElement = new Element("option");
    optionElement.setAttribute("name", name);
    optionElement.setAttribute("value", value);
    element.addContent(optionElement);
  }

  private static void addNewDependency(ModuleSettings moduleSettings, String androidPlatformName) {
    Element moduleManagerElement = moduleSettings.getComponentElement(NEW_MODULE_MANAGER);
    if (moduleManagerElement != null) {
      Element newEntryElement = new Element(OrderEntryFactory.ORDER_ENTRY_ELEMENT_NAME);
      newEntryElement.setAttribute("type", "library");
      newEntryElement.setAttribute("name", androidPlatformName);
      newEntryElement.setAttribute("level", "application");
      moduleManagerElement.addContent(newEntryElement);
    }
  }

  private static void removeAndroidModuleLibrary(ModuleSettings moduleSettings) {
    Element moduleManagerElement = moduleSettings.getComponentElement(NEW_MODULE_MANAGER);
    if (moduleManagerElement != null) {
      for (Element entryElement : getChildren(moduleManagerElement, OrderEntryFactory.ORDER_ENTRY_ELEMENT_NAME)) {
        if ("module-library".equals(entryElement.getAttributeValue("type"))) {
          Element libraryElement = entryElement.getChild("library");
          if (libraryElement != null && ANDROID_MODULE_LIBRARY_NAME.equals(libraryElement.getAttributeValue("name"))) {
            moduleManagerElement.removeContent(entryElement);
            break;
          }
        }
      }
    }
  }

  @Override
  public void process(ModuleSettings moduleSettings) throws CannotConvertException {
    Element confElement = findAndroidFacetConfigurationElement(moduleSettings);
    assert confElement != null;
    String sdkPath = getOptionValue(confElement, SDK_PATH_ATTRIBUTE);
    if (sdkPath == null) return;
    String targetName = getOptionValue(confElement, TARGET_NAME_ATTRIBUTE);
    removeAndroidModuleLibrary(moduleSettings);
    Library library = getAndroidPlatform(sdkPath, targetName);
    if (library != null) {
      addNewDependency(moduleSettings, library.getName());
    }
    removeAllChildren(confElement);
    addOption(confElement, PLATFORM_NAME_ATTRIBUTE, library != null ? library.getName() : "");

  }

  @Nullable
  private static Library getAndroidPlatform(String sdkPath, @Nullable String targetName) {
    AndroidSdk sdk = AndroidSdk.parse(sdkPath, new EmptySdkLog());
    if (sdk != null) {
      IAndroidTarget target = targetName != null ? sdk.findTargetByName(targetName) : sdk.getNewerPlatformTarget();
      if (target != null) {
        Library library = AndroidUtils.findAppropriateAndroidPlatform(target, sdk);
        if (library == null) {
          final LibraryTable.ModifiableModel model = LibraryTablesRegistrar.getInstance().getLibraryTable().getModifiableModel();
          AndroidLibraryManager manager = new AndroidLibraryManager(model);
          library = manager.createNewAndroidPlatform(target, sdkPath);
          manager.apply();
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              model.commit();
            }
          });
        }
        return library;
      }
    }
    return null;
  }

  private static void removeAllChildren(Element element) {
    for (Iterator it = element.getChildren().iterator(); it.hasNext();) {
      it.next();
      it.remove();
    }
  }
}
