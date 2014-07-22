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
package com.intellij.xdebugger.impl.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class SteppingConfigurable extends MergedCompositeConfigurable {
  public SteppingConfigurable(@NotNull List<Configurable> configurables) {
    super(configurables.toArray(new Configurable[configurables.size()]));
  }

  @NotNull
  @Override
  public String getId() {
    return "debugger.stepping";
  }

  @Nls
  @Override
  public String getDisplayName() {
    return XDebuggerBundle.message("debugger.stepping.display.name");
  }
}