/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.roots.OrderEntry;
import org.jetbrains.annotations.NotNull;

public abstract class OrderEntryBaseImpl extends RootModelComponentBase implements OrderEntry {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.OrderEntryVeryBaseImpl");

  private int myIndex;
  private static int _hc;

  @SuppressWarnings({"AssignmentToStaticFieldFromInstanceMethod"})
  private final int hc = _hc++;

  protected OrderEntryBaseImpl(@NotNull RootModelImpl rootModel) {
    super(rootModel);
  }

  void setIndex(int index) {
    myIndex = index;
  }

  @Override
  public int compareTo(@NotNull OrderEntry orderEntry) {
    LOG.assertTrue(orderEntry.getOwnerModule() == getOwnerModule());
    return myIndex - ((OrderEntryBaseImpl)orderEntry).myIndex;
  }

  boolean sameType(@NotNull OrderEntry that) {
    return getClass().equals(that.getClass());
  }

  @Override
  @SuppressWarnings({"EqualsWhichDoesntCheckParameterClass"})
  public final boolean equals(Object obj) {
    return obj == this;
  }

  @Override
  public final int hashCode() {
    return hc;
  }

  @NotNull
  @Override
  public String toString() {
    return getOwnerModule().getName() + " -> " + getPresentableName();
  }
}
