/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes.issueLinks;

import com.intellij.ide.BrowserUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.event.MouseEvent;

public abstract class AbstractBaseTagMouseListener extends LinkMouseListenerBase {
  @Override
  public boolean onClick(@NotNull MouseEvent e, int clickCount) {
    if (e.getButton() == 1 && !e.isPopupTrigger()) {
      Object tag = getTagAt(e);
      if (tag instanceof Runnable) {
        ((Runnable) tag).run();
        return true;
      }

      if (tag != null && !Object.class.getName().equals(tag.getClass().getName())) {
        BrowserUtil.browse(tag.toString());
        return true;
      }
    }
    return false;
  }
}
