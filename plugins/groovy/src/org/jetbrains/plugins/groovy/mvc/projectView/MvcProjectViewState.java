// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.mvc.projectView;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey Evdokimov
 */
public class MvcProjectViewState implements PersistentStateComponent<MvcProjectViewState> {

  public boolean autoScrollFromSource;
  public boolean autoScrollToSource;
  public boolean hideEmptyMiddlePackages = true;

  @Nullable
  @Override
  public MvcProjectViewState getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull MvcProjectViewState state) {
    XmlSerializerUtil.copyBean(state, this);
  }

}
