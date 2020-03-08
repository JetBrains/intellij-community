/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.ui.actions;

import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.impl.MainVcsLogUiProperties;
import com.intellij.vcs.log.impl.VcsLogUiProperties;

public class ShowChangesFromParentsAction extends BooleanPropertyToggleAction {

  public ShowChangesFromParentsAction() {
    super(VcsLogBundle.messagePointer("vcs.log.action.show.all.changes.from.parent"),
          VcsLogBundle.messagePointer("vcs.log.action.description.show.all.changes.from.parent"), null);
  }

  @Override
  protected VcsLogUiProperties.VcsLogUiProperty<Boolean> getProperty() {
    return MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS;
  }
}
