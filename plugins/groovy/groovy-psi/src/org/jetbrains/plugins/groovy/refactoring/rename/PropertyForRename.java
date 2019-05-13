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
package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.light.LightElement;
import org.jetbrains.plugins.groovy.GroovyLanguage;

import java.util.List;

/**
* @author Maxim.Medvedev
*/
public class PropertyForRename extends LightElement {
  private final List<? extends PsiElement> myToRename;
  private final String myPropertyName;

  PropertyForRename(List<? extends PsiElement> toRename, String propertyName, PsiManager manager) {
    super(manager, GroovyLanguage.INSTANCE);

    myToRename = toRename;
    myPropertyName = propertyName;
  }

  public List<? extends PsiElement> getElementsToRename() {
    return myToRename;
  }

  public String getPropertyName() {
    return myPropertyName;
  }

  @Override
  public String toString() {
    return "property for rename";
  }

  @Override
  public boolean isWritable() {
    return true;
  }
}
