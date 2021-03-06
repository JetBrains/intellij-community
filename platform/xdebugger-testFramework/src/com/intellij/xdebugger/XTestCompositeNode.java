/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.xdebugger;

import com.intellij.openapi.util.Pair;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.intellij.xdebugger.frame.XValueContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class XTestCompositeNode extends XTestContainer<XValue> implements XCompositeNode {
  private final @NotNull AtomicReference<Runnable> myNextChildrenRunnableReference = new AtomicReference<>();

  public XTestCompositeNode() {
  }

  public XTestCompositeNode(@NotNull XValueContainer value) {
    myNextChildrenRunnableReference.set(() -> value.computeChildren(this));
  }

  @Override
  public void addChildren(@NotNull XValueChildrenList children, boolean last) {
    final List<XValue> list = new ArrayList<>();
    for (int i = 0; i < children.size(); i++) {
      list.add(children.getValue(i));
    }
    addChildren(list, last);
  }

  @Override
  public final void tooManyChildren(int remaining) {
    tooManyChildren(remaining, null);
  }

  @Override
  public void tooManyChildren(int remaining, @Nullable Runnable addNextChildren) {
    myNextChildrenRunnableReference.set(addNextChildren);
    super.tooManyChildren(remaining);
  }

  @NotNull
  public List<XValue> collectChildren() {
    return collectChildren(XDebuggerTestUtil.TIMEOUT_MS);
  }

  @NotNull
  public List<XValue> collectChildren(long timeoutMs) {
    final Pair<List<XValue>, String> childrenWithError = collectChildrenWithError(timeoutMs);
    final String error = childrenWithError.second;
    assertNull("Error getting children: " + error, error);
    return childrenWithError.first;
  }

  public @NotNull Pair<List<XValue>, String> collectChildrenWithError() {
    return collectChildrenWithError(XDebuggerTestUtil.TIMEOUT_MS);
  }

  public @NotNull Pair<List<XValue>, String> collectChildrenWithError(long timeoutMs) {
    Runnable runnable = myNextChildrenRunnableReference.getAndSet(null);
    assertNotNull("Not expecting children", runnable);
    reset();
    runnable.run();
    return waitFor(timeoutMs);
  }

  @Override
  public void setAlreadySorted(boolean alreadySorted) {
  }
}
