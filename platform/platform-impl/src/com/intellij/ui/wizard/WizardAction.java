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

import javax.swing.*;
import java.awt.event.ActionEvent;

public abstract class WizardAction extends AbstractAction {

  protected WizardModel myModel;

  public WizardAction(String name, WizardModel model) {
    super(name);
    myModel = model;
  }

  protected void setMnemonic(char value) {
    putValue(Action.MNEMONIC_KEY, new Integer(value));
  }

  public final void setName(String name) {
    putValue(Action.NAME, name);
  }

  public static class Next extends WizardAction {

    public Next(WizardModel model) {
      super("Next >", model);
      setMnemonic('N');
    }

    public void actionPerformed(ActionEvent e) {
      myModel.next();
    }
  }

  public static class Previous extends WizardAction {

    public Previous(WizardModel model) {
      super("< Previous", model);
      setMnemonic('P');
    }

    public void actionPerformed(ActionEvent e) {
      myModel.previous();
    }
  }

  public static class Finish extends WizardAction {

    public Finish(WizardModel model) {
      super("Finish", model);
      setMnemonic('F');
    }

    public void actionPerformed(ActionEvent e) {
      myModel.finish();
    }
  }

  public static class Cancel extends WizardAction {

    public Cancel(WizardModel model) {
      super("Cancel", model);
      setMnemonic('C');
    }

    public void actionPerformed(ActionEvent e) {
      myModel.cancel();
    }
  }


}