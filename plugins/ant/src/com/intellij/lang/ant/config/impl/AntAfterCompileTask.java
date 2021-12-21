// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import org.jetbrains.annotations.NotNull;

class AntAfterCompileTask implements CompileTask {
  @Override
  public boolean execute(@NotNull CompileContext context) {
    return AntBeforeCompileTask.initializeAndRun(context, antConfiguration -> {
      if (context.getMessageCount(CompilerMessageCategory.ERROR) > 0) {
        final AntBuildTarget target = antConfiguration.getTargetForEvent(ExecuteAfterCompilationEvent.getInstance());
        if (target != null) {
          String message = AntBundle.message("message.skip.ant.target.after.compilation.errors", target.getDisplayName());
          context.addMessage(CompilerMessageCategory.INFORMATION, message, null, -1, -1);
        }
        return true;
      }
      return antConfiguration.executeTargetAfterCompile(AntBeforeCompileTask.createDataContext(context));
    });
  }
}
