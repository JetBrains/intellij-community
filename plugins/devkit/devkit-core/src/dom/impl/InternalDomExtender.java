// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.converters.values.BooleanValueConverter;
import com.intellij.util.xml.reflect.DomExtender;
import com.intellij.util.xml.reflect.DomExtensionsRegistrar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.Action;
import org.jetbrains.idea.devkit.dom.Group;

/**
 * @author Yann C&eacute;bron
 */
public class InternalDomExtender {

  private static void addInternalAttribute(@NotNull DomExtensionsRegistrar registrar, Class<? extends DomElement> clazz) {
    if (!ApplicationManager.getApplication().isInternal()) {
      return;
    }

    registrar.registerGenericAttributeValueChildExtension(new XmlName("internal"), clazz)
      .setConverter(BooleanValueConverter.getInstance(false));
    registrar.registerGenericAttributeValueChildExtension(new XmlName("overrides"), clazz)
      .setConverter(BooleanValueConverter.getInstance(false));
  }

  static final class ForAction extends DomExtender<Action> {
    @Override
    public void registerExtensions(@NotNull Action action, @NotNull DomExtensionsRegistrar registrar) {
      addInternalAttribute(registrar, Action.class);
    }
  }

  static final class ForGroup extends DomExtender<Group> {
    @Override
    public void registerExtensions(@NotNull Group group, @NotNull DomExtensionsRegistrar registrar) {
      addInternalAttribute(registrar, Group.class);
      registrar.registerGenericAttributeValueChildExtension(new XmlName("keep-content"), Group.class)
        .setConverter(BooleanValueConverter.getInstance(false));
    }
  }
}
