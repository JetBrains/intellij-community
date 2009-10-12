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
package com.intellij.lang.ant.misc;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProject;
import org.jetbrains.annotations.Nullable;

public class AntPsiUtil {

  private AntPsiUtil() {
  }

  /**
   * Returns an element under Ant project which is an ancestor of the specified element.
   * Returns null for AntProject and AntFile.
   */
  @Nullable
  public static AntElement getSubProjectElement(AntElement element) {
    AntElement parent = element.getAntParent();
    while (true) {
      if (parent == null) {
        element = null;
        break;
      }
      if (parent instanceof AntProject) break;
      element = parent;
      parent = parent.getAntParent();
    }
    return element;
  }
}
