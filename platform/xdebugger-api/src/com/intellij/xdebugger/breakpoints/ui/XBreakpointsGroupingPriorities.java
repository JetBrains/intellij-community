/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.xdebugger.breakpoints.ui;

public class XBreakpointsGroupingPriorities {
  public static final int DEFAULT = 100;
  public static final int BY_CLASS = 400;
  public static final int BY_FILE = 600;
  public static final int BY_PACKAGE = 800;
  public static final int BY_TYPE = 1000;
}
