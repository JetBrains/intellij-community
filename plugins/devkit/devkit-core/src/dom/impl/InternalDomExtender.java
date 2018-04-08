/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.openapi.application.ApplicationManager;
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

  private static void addInternalAttribute(@NotNull DomExtensionsRegistrar registrar, Class clazz) {
    if (!ApplicationManager.getApplication().isInternal()) {
      return;
    }

    registrar.registerGenericAttributeValueChildExtension(new XmlName("internal"), clazz)
      .setConverter(BooleanValueConverter.getInstance(false));
    registrar.registerGenericAttributeValueChildExtension(new XmlName("overrides"), clazz)
      .setConverter(BooleanValueConverter.getInstance(false));
  }

  public static class ForAction extends DomExtender<Action> {
    @Override
    public void registerExtensions(@NotNull Action action, @NotNull DomExtensionsRegistrar registrar) {
      addInternalAttribute(registrar, Action.class);
    }
  }

  public static class ForGroup extends DomExtender<Group> {
    @Override
    public void registerExtensions(@NotNull Group group, @NotNull DomExtensionsRegistrar registrar) {
      addInternalAttribute(registrar, Group.class);
      registrar.registerGenericAttributeValueChildExtension(new XmlName("keep-content"), Group.class)
        .setConverter(BooleanValueConverter.getInstance(false));
    }
  }
}
