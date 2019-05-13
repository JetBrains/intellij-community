/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.debugger;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.actions.JvmSmartStepIntoHandler;
import com.intellij.debugger.actions.SmartStepTarget;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.openapi.fileEditor.TextEditor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;

import java.util.Collections;
import java.util.List;

/**
 * @author egor
 */
public class GroovySmartStepIntoHandler extends JvmSmartStepIntoHandler {
  @NotNull
  @Override
  public List<SmartStepTarget> findSmartStepTargets(SourcePosition position) {
    return Collections.emptyList();
  }

  @Override
  public boolean doSmartStep(SourcePosition position, DebuggerSession session, TextEditor fileEditor) {
    doStepInto(session, false, null);
    return true;
  }

  @Override
  public boolean isAvailable(SourcePosition position) {
    return position.getFile().getLanguage().isKindOf(GroovyLanguage.INSTANCE);
  }
}
