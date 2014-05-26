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
package com.intellij.xdebugger.impl;

import com.intellij.xdebugger.XDebugProcessStarter;
import com.intellij.xdebugger.impl.ui.XDebugSessionData;

/**
 * Created by avu on 22/05/14.
 */
public abstract class XDebugProcessConfiguratorStarter extends XDebugProcessStarter {
  public abstract void configure(XDebugSessionData data);
}
