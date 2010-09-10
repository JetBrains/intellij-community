/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.history.wholeTree;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.vcs.BigArray;

/**
 * @author irengrig
 */
public class FictiveAction extends AnAction {
  public FictiveAction() {
    super("New Git Log");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final BigArray<VisibleLine> ba = new BigArray<VisibleLine>(4);
    for (int i = 0; i < 100; i++) {
      ba.add(new MyLine("Test" + i));
    }
    GitLogLongPanel.showDialog(PlatformDataKeys.PROJECT.getData(e.getDataContext()));
  }

  private static class MyLine implements VisibleLine {
    private final String myLine;

    public MyLine(String line) {
      myLine = line;
    }

    @Override
    public Object getData() {
      return myLine;
    }
    @Override
    public boolean isDecoration() {
      return false;
    }
  }
}
