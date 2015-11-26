/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.appengine.enhancement;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.appengine.build.EnhancerProcessHandlerBase;

/**
 * @author nik
 */
public class EnhancerProcessHandler extends EnhancerProcessHandlerBase {
  private final CompileContext myContext;

  public EnhancerProcessHandler(final Process process, @NotNull String commandLine, CompileContext context) {
    super(process, commandLine, null);
    myContext = context;
  }

  @Override
  protected void reportInfo(String message) {
    myContext.addMessage(CompilerMessageCategory.INFORMATION, message, null, -1, -1);
  }

  @Override
  protected void reportError(String message) {
    myContext.addMessage(CompilerMessageCategory.ERROR, message, null, -1, -1);
  }
}
