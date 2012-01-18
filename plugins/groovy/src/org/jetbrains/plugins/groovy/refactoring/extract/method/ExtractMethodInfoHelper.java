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

package org.jetbrains.plugins.groovy.refactoring.extract.method;

import com.intellij.psi.PsiModifier;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractInfoHelperBase;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractUtil;
import org.jetbrains.plugins.groovy.refactoring.extract.InitialInfo;

/**
 * @author ilyas
 */
public class ExtractMethodInfoHelper extends ExtractInfoHelperBase {

  private final boolean myIsStatic;
  private boolean mySpecifyType = true;
  private String myVisibility;
  private String myName;

  public ExtractMethodInfoHelper(InitialInfo initialInfo, String name) {
    super(initialInfo);

    myVisibility = PsiModifier.PRIVATE;
    myName = name;

    myIsStatic = ExtractUtil.canBeStatic(initialInfo.getStatements()[0]);
  }

  public boolean isStatic() {
    return myIsStatic;
  }

  public String getVisibility() {
    return myVisibility;
  }

  public void setVisibility(String visibility) {
    myVisibility = visibility;
  }

  public boolean specifyType() {
    return mySpecifyType;
  }

  public void setSpecifyType(boolean specifyType) {
    mySpecifyType = specifyType;
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }
}
