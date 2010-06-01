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
package com.intellij.openapi.wm.ex;

import com.intellij.openapi.*;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.wm.*;
import org.jetbrains.annotations.*;

import javax.swing.*;
import javax.swing.event.*;

/**
 * @author spleaner
 */
public interface StatusBarEx extends StatusBar, Disposable {
  void startRefreshIndication(String tooltipText);
  void stopRefreshIndication();

  BalloonHandler notifyProgressByBalloon(@NotNull MessageType type, @NotNull String htmlBody);
  BalloonHandler notifyProgressByBalloon(@NotNull MessageType type, @NotNull String htmlBody, @Nullable Icon icon, @Nullable HyperlinkListener listener);

  void addProgress(ProgressIndicatorEx indicator, TaskInfo info);

  void updateWidgets();

  boolean isProcessWindowOpen();

  void setProcessWindowOpen(boolean open);
}
