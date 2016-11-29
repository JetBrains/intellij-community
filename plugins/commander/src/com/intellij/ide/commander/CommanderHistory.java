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
package com.intellij.ide.commander;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiUtilBase;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 14, 2004
 */
public class CommanderHistory {
  public static final int HISTORY_LIMIT = 2*30; // elements are saved in pairs
  private final Commander myCommander;
  private final List<HistoryState> myHistory = new ArrayList<>();
  private int myCurrentCommandIndex = 0;
  private boolean myStateLoggingEnabled = true;

  public CommanderHistory(Commander commander) {
    myCommander = commander;
  }

  public void clearHistory() {
    myHistory.clear();
    myCurrentCommandIndex = 0;
  }

  public void saveState(final PsiElement element, boolean isElementExpanded, final boolean isLeftPanel) {
    if (!myStateLoggingEnabled) {
      return;
    }
    if (myCurrentCommandIndex >=0 && myCurrentCommandIndex < myHistory.size() - 1) {
      myHistory.subList(myCurrentCommandIndex + 1, myHistory.size()).clear();
    }
    if (myHistory.size() == HISTORY_LIMIT) {
      myHistory.remove(0);
    }
    myHistory.add(new HistoryState(element, isElementExpanded, isLeftPanel));
    myCurrentCommandIndex = myHistory.size() - 1;
  }

  public void back() {
    if (applyState(getHistoryState(myCurrentCommandIndex - 1))) {
      myCurrentCommandIndex--;
    }
  }

  public boolean canGoBack() {
    return getHistoryState(myCurrentCommandIndex - 1) != null;
  }

  public void forward() {
    if (applyState(getHistoryState(myCurrentCommandIndex + 1))) {
      myCurrentCommandIndex++;
    }
  }

  public boolean canGoForward() {
    return getHistoryState(myCurrentCommandIndex + 1) != null;
  }

  private boolean applyState(HistoryState state) {
    myStateLoggingEnabled = false;
    try {
      if (state != null) {
        final PsiElement element = state.getElement();
        final boolean shouldOpenInLeftPanel = state.isInLeftPanel();
        if (state.isElementExpanded()) {
          final boolean isLeftPanelCurrentlyActive = myCommander.isLeftPanelActive();
          if ((shouldOpenInLeftPanel && !isLeftPanelCurrentlyActive) || (!shouldOpenInLeftPanel && isLeftPanelCurrentlyActive)) {
            myCommander.switchActivePanel();
          }
          myCommander.enterElementInActivePanel(element);
        }
        else {
          if (shouldOpenInLeftPanel) {
            myCommander.selectElementInLeftPanel(element, PsiUtilBase.getVirtualFile(element));
          }
          else {
            myCommander.selectElementInRightPanel(element, PsiUtilBase.getVirtualFile(element));
          }
        }
        return true;
      }
    }
    finally {
      myStateLoggingEnabled = true;
    }
    return false;
  }

  private HistoryState getHistoryState(int index) {
    if (index >= 0 && index < myHistory.size()) {
      return myHistory.get(index);
    }
    return null;
  }

  private static final class HistoryState {
    private final PsiElement myElement;
    private final boolean myElementExpanded;
    private final boolean myIsLeftPanel;

    public HistoryState(PsiElement element, boolean isElementExpanded, boolean isLeftPanel) {
      myElement = element;
      myElementExpanded = isElementExpanded;
      myIsLeftPanel = isLeftPanel;
    }

    public boolean isInLeftPanel() {
      return myIsLeftPanel;
    }

    public boolean isElementExpanded() {
      return myElementExpanded;
    }

    public PsiElement getElement() {
      return myElement;
    }
  }
}
