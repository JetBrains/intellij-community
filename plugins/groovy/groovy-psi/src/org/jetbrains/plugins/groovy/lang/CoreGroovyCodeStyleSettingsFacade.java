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
package org.jetbrains.plugins.groovy.lang;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyCodeStyleSettingsFacade;

public class CoreGroovyCodeStyleSettingsFacade extends GroovyCodeStyleSettingsFacade {
  @Override
  public boolean useFqClassNames() {
    return false;
  }

  @Override
  public boolean useFqClassNamesInJavadoc() {
    return false;
  }

  @Override
  public int staticFieldsOrderWeight() {
    return 0;
  }

  @Override
  public int fieldsOrderWeight() {
    return 0;
  }

  @Override
  public int staticMethodsOrderWeight() {
    return 0;
  }

  @Override
  public int methodsOrderWeight() {
    return 0;
  }

  @Override
  public int staticInnerClassesOrderWeight() {
    return 0;
  }

  @Override
  public int innerClassesOrderWeight() {
    return 0;
  }

  @Override
  public int constructorsOrderWeight() {
    return 0;
  }

  @Override
  public boolean insertInnerClassImports() {
    return false;
  }
}
