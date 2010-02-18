/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.xdebugger.breakpoints;

import org.jetbrains.annotations.NotNull;

/**
 * Represent a breakpoint which is set on some line in a file. This interface isn't supposed to be implemented by a plugin. In order to
 * support breakpoint provide {@link XLineBreakpointType} implementation
 *
 * @author nik
 */
public interface XLineBreakpoint<P extends XBreakpointProperties> extends XBreakpoint<P> {

  int getLine();

  String getFileUrl();

  String getPresentableFilePath();

  @NotNull
  XLineBreakpointType<P> getType();
}
