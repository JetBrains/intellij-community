// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.util.InvalidDataException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer;

final class OrderEntryFactory {
  static @NotNull OrderEntry createOrderEntryByElement(@NotNull Element element,
                                                       @NotNull RootModelImpl rootModel,
                                                       @NotNull ProjectRootManagerImpl projectRootManager) throws InvalidDataException {
    String type = element.getAttributeValue(JpsModuleRootModelSerializer.TYPE_ATTRIBUTE);
    if (type == null) {
      throw new InvalidDataException();
    }

    switch (type) {
      case ModuleSourceOrderEntryImpl.ENTRY_TYPE:
        return new ModuleSourceOrderEntryImpl(element, rootModel);
      case ModuleJdkOrderEntryImpl.ENTRY_TYPE:
        return new ModuleJdkOrderEntryImpl(element, rootModel, projectRootManager);
      case InheritedJdkOrderEntryImpl.ENTRY_TYPE:
        return new InheritedJdkOrderEntryImpl(element, rootModel, projectRootManager);
      case LibraryOrderEntryImpl.ENTRY_TYPE:
        return new LibraryOrderEntryImpl(element, rootModel, projectRootManager);
      case ModuleLibraryOrderEntryImpl.ENTRY_TYPE:
        return new ModuleLibraryOrderEntryImpl(element, rootModel, projectRootManager);
      case ModuleOrderEntryImpl.ENTRY_TYPE:
        return new ModuleOrderEntryImpl(element, rootModel);
      default:
        throw new InvalidDataException("Unknown order entry type:" + type);
    }
  }

  static @NotNull Element createOrderEntryElement(@NotNull String type) {
    Element element = new Element(JpsModuleRootModelSerializer.ORDER_ENTRY_TAG);
    element.setAttribute(JpsModuleRootModelSerializer.TYPE_ATTRIBUTE, type);
    return element;
  }
}
