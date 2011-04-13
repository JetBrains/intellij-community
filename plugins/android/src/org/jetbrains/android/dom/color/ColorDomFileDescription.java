/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.android.dom.color;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.dom.AndroidResourceDomFileDescription;

/**
 * @author Eugene.Kudelevsky
 */
public class ColorDomFileDescription extends AndroidResourceDomFileDescription<ColorSelector> {
  public ColorDomFileDescription() {
    super(ColorSelector.class, "selector", "color");
  }

  @Override
  public boolean acceptsOtherRootTagNames() {
    return false;
  }

  public static boolean isColorResourceFile(final XmlFile file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return new ColorDomFileDescription().isMyFile(file, null);
      }
    });
  }
}
