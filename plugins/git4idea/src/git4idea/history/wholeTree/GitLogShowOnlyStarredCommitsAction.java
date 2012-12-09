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
package git4idea.history.wholeTree;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.util.OnOff;
import icons.Git4ideaIcons;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 6/29/12
 * Time: 5:46 PM
 */
public class GitLogShowOnlyStarredCommitsAction extends ToggleAction implements DumbAware {
  private final OnOff myOnOff;

  public GitLogShowOnlyStarredCommitsAction(final OnOff onOff) {
    super("Filter Starred", "Filter Starred Commits", Git4ideaIcons.Star);
    myOnOff = onOff;
  }

  /**
   * Returns the selected (checked, pressed) state of the action.
   *
   * @param e the action event representing the place and context in which the selected state is queried.
   * @return true if the action is selected, false otherwise
   */
  @Override
  public boolean isSelected(AnActionEvent e) {
    return myOnOff.isOn();
  }

  /**
   * Sets the selected state of the action to the specified value.
   *
   * @param e     the action event which caused the state change.
   * @param state the new selected state of the action.
   */
  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    if (myOnOff.isOn()) {
      myOnOff.off();
    } else {
      myOnOff.on();
    }
  }
}
