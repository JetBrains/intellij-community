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
package com.intellij.openapi.vcs.changes;

import com.intellij.ide.dnd.DnDTarget;
import com.intellij.ui.content.impl.ContentImpl;

import javax.swing.*;
import java.awt.*;

public abstract class DnDTargetContentAdapter extends ContentImpl implements DnDTarget {
  protected DnDTargetContentAdapter(JComponent component, String displayName, boolean isLockable) {
    super(component, displayName, isLockable);
  }

  @Override
  public void cleanUpOnLeave() {
  }

  @Override
  public void updateDraggedImage(Image image, Point dropPoint, Point imageOffset) {
  }
}