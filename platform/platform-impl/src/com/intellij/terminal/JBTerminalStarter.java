/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.terminal;

import com.jediterm.terminal.*;
import com.jediterm.terminal.emulator.JediEmulator;

/**
 * @author traff
 */
public class JBTerminalStarter extends TerminalStarter {
  public JBTerminalStarter(Terminal terminal, TtyConnector ttyConnector) {
    super(terminal, ttyConnector, new TtyBasedArrayDataStream(ttyConnector));
  }

  @Override
  protected JediEmulator createEmulator(TerminalDataStream dataStream, Terminal terminal) {
    return new JediEmulator(dataStream, terminal) {
      @Override
      protected void unsupported(char... sequenceChars) {
        if (sequenceChars[0] == 7) { //ESC BEL
          JBTerminalPanel.refreshAfterExecution();
        }
        else {
          super.unsupported();
        }
      }
    };
  }
}
