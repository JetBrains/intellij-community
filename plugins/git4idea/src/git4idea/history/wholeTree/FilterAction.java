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
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;

/**
 * @author irengrig
 */
public class FilterAction extends BasePopupAction {
  @NonNls public static final String NONE = "None";

  public FilterAction(Project project) {
    super(project, "Filter by:");
    myLabel.setText(NONE);
  }

  @Override
  protected DefaultActionGroup createActionGroup() {
    final DefaultActionGroup group = new DefaultActionGroup();
    group.add(new FictiveAction(NONE));
    group.add(new FictiveAction("User"));
    group.add(new FictiveAction("Structure"));
    return group;
  }

  private class FictiveAction extends AnAction {
    private FictiveAction(String text) {
      super(text);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myLabel.setText(e.getPresentation().getText());
    }
  }
}
