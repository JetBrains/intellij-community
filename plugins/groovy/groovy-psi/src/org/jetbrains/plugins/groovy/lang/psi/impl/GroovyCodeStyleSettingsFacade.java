// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.project.Project;

public abstract class GroovyCodeStyleSettingsFacade {

  public static GroovyCodeStyleSettingsFacade getInstance(Project project) {
    return project.getService(GroovyCodeStyleSettingsFacade.class);
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
