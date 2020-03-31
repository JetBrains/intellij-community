// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerMessageCategory;

class AntAfterCompileTask implements CompileTask {
  @Override
  public boolean execute(CompileContext context) {
    return AntBeforeCompileTask.initializeAndRun(context, antConfiguration -> {
      if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
        final AntBuildTarget target = antConfiguration.getTargetForEvent(ExecuteAfterCompilationEvent.getInstance());
        if (target != null) {
          context.addMessage(CompilerMessageCategory.INFORMATION,
                             "Skipping ant target \"" + target.getDisplayName() + "\" because of compilation errors", null, -1, -1);
        }
        return true;
      }
      return antConfiguration.executeTargetAfterCompile(AntBeforeCompileTask.createDataContext(context));
    });
  }
}
