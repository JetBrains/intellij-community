// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger;

import com.intellij.openapi.util.Pair;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.SmartList;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class XTestContainer<T> {
  private List<T> myChildren;
  private CompletableFuture<Pair<List<T>, String>> myResultFuture;

  public XTestContainer() {
    reset();
  }

  protected void reset() {
    myChildren = new SmartList<>();
    myResultFuture = new CompletableFuture<>();
  }

  public void addChildren(List<? extends T> children, boolean last) {
    myChildren.addAll(children);
    if (last) done(null);
  }

  public void tooManyChildren(int remaining) {
    done(null);
  }

  private void done(@Nullable String errorMessage) {
    myResultFuture.complete(Pair.create(myChildren, errorMessage));
  }

  public void setMessage(@NotNull String message,
                         Icon icon,
                         @NotNull final SimpleTextAttributes attributes,
                         @Nullable XDebuggerTreeNodeHyperlink link) {
  }

  public void setErrorMessage(@NotNull String message, @Nullable XDebuggerTreeNodeHyperlink link) {
    setErrorMessage(message);
  }

  public void setErrorMessage(@NotNull String errorMessage) {
    done(errorMessage);
  }

  @NotNull
  public Pair<List<T>, String> waitFor(long timeoutMs) {
    Pair<List<T>, String> result = XDebuggerTestUtil.waitFor(myResultFuture, timeoutMs);
    Assert.assertNotNull("Timed out", result);
    return result;
  }
}
