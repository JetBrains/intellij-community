// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.jediterm.terminal.*;
import com.jediterm.terminal.emulator.JediEmulator;

public class JBTerminalStarter extends TerminalStarter {
  public JBTerminalStarter(Terminal terminal, TtyConnector ttyConnector) {
    super(terminal, ttyConnector, new TtyBasedArrayDataStream(ttyConnector));
  }

  @Override
  protected JediEmulator createEmulator(TerminalDataStream dataStream, Terminal terminal) {
    return new JediEmulator(dataStream, terminal);
  }
}
