// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.util.xml.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface Listeners extends DomElement {
  @NotNull
  @SubTagList("listener")
  @Stubbed
  List<Listener> getListeners();

  interface Listener extends DomElement {
    @Attribute("class")
    @Required
    GenericAttributeValue<String> getListenerClassName();

    @Attribute("topic")
    @Required
    GenericAttributeValue<String> getTopicClassName();

    @Attribute("activeInTestMode")
    GenericAttributeValue<Boolean> isActiveInTestMode();
  }
}
