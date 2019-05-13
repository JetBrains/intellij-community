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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

public abstract class GroovyCodeStyleSettingsFacade {

  public static GroovyCodeStyleSettingsFacade getInstance(Project project) {
    return ServiceManager.getService(project, GroovyCodeStyleSettingsFacade.class);
  }

  public abstract boolean useFqClassNames();
  public abstract boolean useFqClassNamesInJavadoc();

  public abstract int staticFieldsOrderWeight();

  public abstract int fieldsOrderWeight();

  public abstract int staticMethodsOrderWeight();

  public abstract int methodsOrderWeight();

  public abstract int staticInnerClassesOrderWeight();

  public abstract int innerClassesOrderWeight();

  public abstract int constructorsOrderWeight();

  public abstract boolean insertInnerClassImports();
}
