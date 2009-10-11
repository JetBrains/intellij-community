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
package com.intellij.ui.wizard;

public final class WizardNavigationState {

  public final WizardAction PREVIOUS;
  public final WizardAction NEXT;
  public final WizardAction CANCEL;
  public final WizardAction FINISH;

  public WizardNavigationState(WizardModel model) {
    this.PREVIOUS = new WizardAction.Previous(model);
    this.NEXT = new WizardAction.Next(model);
    this.CANCEL = new WizardAction.Cancel(model);
    this.FINISH = new WizardAction.Finish(model);
  }

  public void setEnabledToAll(boolean enabled) {
    PREVIOUS.setEnabled(enabled);
    NEXT.setEnabled(enabled);
    CANCEL.setEnabled(enabled);
    FINISH.setEnabled(enabled);
  }

}

