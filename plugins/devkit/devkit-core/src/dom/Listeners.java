// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.psi.PsiClass;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface Listeners extends DomElement {
  @NotNull
  @SubTagList("listener")
  List<Listener> getListeners();

  interface Listener extends DomElement {
    @Attribute("class")
    @Required
    GenericAttributeValue<PsiClass> getListenerClassName();

    @Attribute("topic")
    @Required
    GenericAttributeValue<PsiClass> getTopicClassName();

    @Attribute("activeInTestMode")
    GenericAttributeValue<Boolean> isActiveInTestMode();

    @Attribute("activeInHeadlessMode")
    GenericAttributeValue<Boolean> isActiveInHeadlessMode();
  }
}
