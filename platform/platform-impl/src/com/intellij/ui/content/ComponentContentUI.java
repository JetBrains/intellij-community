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
package com.intellij.ui.content;


import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.actionSystem.DataProvider;

import javax.swing.*;
import java.awt.*;

public class ComponentContentUI implements ContentUI {
  private ContentManager myManager;
  private final JPanel myPanel;

  public ComponentContentUI() {
    myPanel = new MyPanel();
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void setManager(ContentManager manager) {
    myManager = manager;
    myManager.addContentManagerListener(new MyContentManagerListener());
  }

  private class MyContentManagerListener extends ContentManagerAdapter {
    public void selectionChanged(ContentManagerEvent event) {
      myPanel.removeAll();
      Content content = event.getContent();
      if (content != null) {
        myPanel.add(content.getComponent(), BorderLayout.CENTER);
        myPanel.validate();
        myPanel.repaint();
      }
    }
  }

  private class MyPanel extends JPanel implements DataProvider {
    public MyPanel() {
      super(new BorderLayout());
      setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    }

    public Object getData(String dataId) {
      if (DataConstantsEx.CONTENT_MANAGER.equals(dataId)) {
        return myManager;
      }
      if (DataConstantsEx.NONEMPTY_CONTENT_MANAGER.equals(dataId) && myManager.getContentCount() > 1) {
        return myManager;
      }
      return null;
    }
  }

  public boolean isSingleSelection() {
    return true;
  }

  public boolean isToSelectAddedContent() {
    return true;
  }

  public boolean canBeEmptySelection() {
    return false;
  }

  public void beforeDispose() {
  }

  public void dispose() {
  }
}

