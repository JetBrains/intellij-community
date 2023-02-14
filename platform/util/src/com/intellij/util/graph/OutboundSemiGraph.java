// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.graph;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;

public interface OutboundSemiGraph<Node> {

  @NotNull Collection<Node> getNodes();

  @NotNull Iterator<Node> getOut(Node node);
}