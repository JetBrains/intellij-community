/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.Nullable;

/**
* @author Max Medvedev
*/
class SimpleInfo {
  int myOldIndex;
  String myNewName;
  String myDefaultValue;
  String myDefaultInitializer;
  String myType;
  boolean myFeelLucky;

  SimpleInfo(int oldIndex) {
    this(null, oldIndex);
  }

  SimpleInfo(@Nullable String newName, int oldIndex) {
    this(newName, oldIndex, "", null, "");
  }

  SimpleInfo(@Nullable String newName, int oldIndex, String defaultValue, @Nullable String defaultInitializer, String type) {
    this(newName, oldIndex, defaultValue, defaultInitializer, type, false);
  }

  SimpleInfo(@Nullable String newName, int oldIndex, String defaultValue, @Nullable String defaultInitializer, String type, boolean feelLucky) {
    myOldIndex = oldIndex;
    myNewName = newName;
    myDefaultValue = defaultValue;
    myDefaultInitializer = defaultInitializer;
    myType = type;
    myFeelLucky = feelLucky;
  }

  SimpleInfo(String newName, int oldIndex, String defaultValue, String defaultInitializer, PsiType type) {
    this(newName, oldIndex, defaultValue, defaultInitializer, type.getCanonicalText());
  }
}
