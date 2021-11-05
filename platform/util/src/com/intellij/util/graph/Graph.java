// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.graph;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;

/**
 * @author dsl
 */
public interface Graph<Node> extends InboundSemiGraph<Node>, OutboundSemiGraph<Node> {

  @Override
  @NotNull Collection<Node> getNodes();

  @Override
  @NotNull Iterator<Node> getIn(Node node);

  @Override
  @NotNull Iterator<Node> getOut(Node node);
}