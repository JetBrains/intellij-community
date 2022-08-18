// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom;

import com.intellij.openapi.extensions.ExtensionDescriptor;
import com.intellij.psi.PsiClass;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.impl.PluginPsiClassConverter;

import java.util.List;

public interface Listeners extends DomElement {
  @SubTagList("listener")
  @NotNull List<? extends Listener> getListeners();

  interface Listener extends DomElement {
    @Attribute("class")
    @Required
    @Convert(PluginPsiClassConverter.class)
    @ExtendClass(allowNonPublic = true, allowAbstract = false, allowInterface = false, allowEnum = false, instantiatable = false)
    GenericAttributeValue<PsiClass> getListenerClassName();

    @Attribute("topic")
    @Required
    @Convert(PluginPsiClassConverter.class)
    @ExtendClass(allowNonPublic = true, allowEnum = false, instantiatable = false)
    GenericAttributeValue<PsiClass> getTopicClassName();

    @Attribute("activeInTestMode")
    GenericAttributeValue<Boolean> isActiveInTestMode();

    @Attribute("activeInHeadlessMode")
    GenericAttributeValue<Boolean> isActiveInHeadlessMode();

    @Attribute("os")
    GenericAttributeValue<ExtensionDescriptor.Os> getOs();
  }
}
