/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.introduce.field;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceSettings;

/**
 * @author Maxim.Medvedev
 */
public interface GrIntroduceFieldSettings extends GrIntroduceSettings {
  boolean declareFinal();

  Init initializeIn();

  @GrModifier.GrModifierConstant
  String getVisibilityModifier();

  boolean isStatic();

  boolean removeLocalVar();

  enum Init {
    CUR_METHOD("current method"), FIELD_DECLARATION("field declaration"), CONSTRUCTOR("class constructor(s)"), SETUP_METHOD("setUp method");

    private final String myName;

    Init(@NotNull String name) {
      myName = name;
    }

    @Override
    public String toString() {
      return myName;
    }
  }
}
