// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.agent.util.log;

public interface TerminalListener {

  void close();

  void setTtyResizeHandler(TtyResizeHandler ttyResizeHandler);

  TerminalListener NULL = new TerminalListener() {

    public void close() {
      //
    }

    public void setTtyResizeHandler(TtyResizeHandler ttyResizeHandler) {
      //
    }
  };

  interface TtyResizeHandler {
    void onTtyResizeRequest(int ttyWidth, int ttyHeight);
  }
}
