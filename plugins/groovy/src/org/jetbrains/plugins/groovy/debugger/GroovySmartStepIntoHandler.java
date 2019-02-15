// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    doStepInto(session, false, null, false);
    return true;
  }

  @Override
  public boolean isAvailable(SourcePosition position) {
    return position.getFile().getLanguage().isKindOf(GroovyLanguage.INSTANCE);
  }
}
