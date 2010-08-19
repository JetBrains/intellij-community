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
package com.intellij.xdebugger.breakpoints.ui;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author nik
 */
public abstract class XBreakpointGroup implements Comparable<XBreakpointGroup> {
  @Nullable
  public Icon getIcon(boolean isOpen) {
    return null;
  }

  @NotNull
  public abstract String getName();

  public int compareTo(final XBreakpointGroup o) {
    return getName().compareTo(o.getName());
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof XBreakpointGroup && compareTo((XBreakpointGroup)obj) == 0;
  }

  @Override
  public int hashCode() {
    return getName().hashCode();
  }
}
