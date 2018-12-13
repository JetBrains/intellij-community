// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

public interface JBTerminalWidgetListener {
  void onNewSession();
  void onTerminalStarted();
  void onPreviousTabSelected();
  void onNextTabSelected();
  void onSessionClosed();
  void showTabs();
}
