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

import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceContext;
import org.jetbrains.plugins.groovy.refactoring.introduce.GrIntroduceDialog;

import java.util.LinkedHashSet;

/**
* @author Maxim.Medvedev
*/
class IntroduceFieldTestHandler extends GrIntroduceFieldHandler {

  private boolean myIsStatic;
  private boolean myRemoveLocal;
  private boolean myDeclareFinal;
  private GrIntroduceFieldSettings.Init myInitializeIn;
  private boolean replaceAll;
  private PsiType mySelectedType;

  IntroduceFieldTestHandler(boolean isStatic,
                            boolean removeLocal,
                            boolean declareFinal,
                            GrIntroduceFieldSettings.Init initializeIn,
                            boolean replaceAll,
                            PsiType selectedType) {
    myIsStatic = isStatic;
    myRemoveLocal = removeLocal;
    myDeclareFinal = declareFinal;
    myInitializeIn = initializeIn;
    this.replaceAll = replaceAll;
    mySelectedType = selectedType;
  }

  @NotNull
  @Override
  protected GrIntroduceDialog<GrIntroduceFieldSettings> getDialog(@NotNull GrIntroduceContext context) {
    return new GrIntroduceDialog<GrIntroduceFieldSettings>() {
      @Override
      public GrIntroduceFieldSettings getSettings() {
        return new GrIntroduceFieldSettings() {
          @Override
          public boolean declareFinal() {
            return myDeclareFinal;
          }

          @Override
          public Init initializeIn() {
            return myInitializeIn;
          }

          @Override
          public String getVisibilityModifier() {
            return PsiModifier.PACKAGE_LOCAL;
          }

          @Override
          public boolean isStatic() {
            return myIsStatic;
          }

          @Override
          public boolean removeLocalVar() {
            return myRemoveLocal;
          }

          @Override
          public String getName() {
            return "f";
          }

          @Override
          public boolean replaceAllOccurrences() {
            return replaceAll;
          }

          @Override
          public PsiType getSelectedType() {
            return mySelectedType;
          }
        };
      }

      @Override
      public void show() {
      }

      @Override
      public boolean isOK() {
        return true;
      }

      @NotNull
      @Override
      public LinkedHashSet<String> suggestNames() {
        LinkedHashSet<String> strings = new LinkedHashSet<>();
        strings.add("f");
        return strings;
      }
    };
  }
}
