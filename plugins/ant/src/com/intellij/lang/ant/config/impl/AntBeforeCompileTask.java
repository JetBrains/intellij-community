// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.openapi.compiler.CompileContext;
import org.jetbrains.annotations.NotNull;

class AntBeforeCompileTask extends AntCompileTask {
  @Override
  public boolean execute(@NotNull CompileContext context) {
    return AntConfigurationBase.getInstance(context.getProject()).executeTargetBeforeCompile(context, createDataContext(context));
  }

}