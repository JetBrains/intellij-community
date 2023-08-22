// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.impl;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static com.intellij.ide.macro.CompilerContextMakeMacro.COMPILER_CONTEXT_MAKE_KEY;

abstract class AntCompileTask implements CompileTask {

  @NotNull
  protected static DataContext createDataContext(CompileContext context) {
    Project project = context.getProject();
    CompileScope scope = context.getCompileScope();
    Module[] modules = ReadAction.compute(() -> scope.getAffectedModules());
    return SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(PlatformCoreDataKeys.MODULE, modules.length == 1? modules[0] : null)
      .add(LangDataKeys.MODULE_CONTEXT_ARRAY, modules)
      .add(COMPILER_CONTEXT_MAKE_KEY, context.isMake())
      .build();
  }
}
