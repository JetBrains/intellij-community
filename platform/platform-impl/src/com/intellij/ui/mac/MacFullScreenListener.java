/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ui.mac;

import com.apple.eawt.AppEvent;
import com.apple.eawt.FullScreenAdapter;
import com.intellij.Patches;
import com.intellij.openapi.wm.impl.IdeFrameImpl;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class MacFullScreenListener extends FullScreenAdapter {
  private final MacMainFrameDecorator myDecorator;
  private final IdeFrameImpl myFrame;

  public MacFullScreenListener(final MacMainFrameDecorator decorator,
                               final IdeFrameImpl frame) {
    myDecorator = decorator;
    myFrame = frame;
  }

  @Override
  public void windowEnteredFullScreen(AppEvent.FullScreenEvent event) {
    myDecorator.setInFullScreen(true);

    JRootPane rootPane = myFrame.getRootPane();
    if (rootPane != null) rootPane.putClientProperty(MacMainFrameDecorator.FULL_SCREEN, Boolean.TRUE);
    if (Patches.APPLE_BUG_ID_10207064) {
      // fix problem with bottom empty bar
      // it seems like the title is still visible in fullscreen but the window itself shifted up for titlebar height
      // and the size of the frame is still calculated to be the height of the screen which is wrong
      // so just add these titlebar height to the frame height once again
      Timer timer = new Timer(300, new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              myFrame.setSize(myFrame.getWidth(), myFrame.getHeight() + myFrame.getInsets().top);
            }
          });
        }
      });
      timer.setRepeats(false);
      timer.start();
    }
  }

  @Override
  public void windowExitedFullScreen(AppEvent.FullScreenEvent event) {
    myDecorator.setInFullScreen(false);
    myFrame.storeFullScreenStateIfNeeded(false);

    JRootPane rootPane = myFrame.getRootPane();
    if (rootPane != null) rootPane.putClientProperty(MacMainFrameDecorator.FULL_SCREEN, null);
  }
}
