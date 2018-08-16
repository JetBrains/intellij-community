// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.impl.runtime.ui;

import com.intellij.ide.util.treeView.TreeVisitor;
import org.jetbrains.annotations.NotNull;

public interface TreeNodeSelector<T> extends TreeVisitor<T> {
  @Override
  boolean visit(@NotNull T node);

  Class<T> getNodeClass();
}
