// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.Stubbed;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface Actions extends DomElement {

  @NotNull
  @Stubbed
  List<Action> getActions();

  Action addAction();


  @NotNull
  @Stubbed
  List<Group> getGroups();

  Group addGroup();


  @NotNull
  List<Reference> getReferences();

  Reference addReference();

  @NotNull
  List<Unregister> getUnregisters();
}
