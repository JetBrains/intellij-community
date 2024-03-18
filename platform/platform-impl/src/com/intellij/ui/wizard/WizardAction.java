// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.wizard;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.NlsActions;

import javax.swing.*;
import java.awt.event.ActionEvent;

public abstract class WizardAction extends AbstractAction {

  protected WizardModel myModel;

  public WizardAction(@NlsActions.ActionText String name, WizardModel model) {
    super(name);
    myModel = model;
  }

  protected void setMnemonic(char value) {
    putValue(Action.MNEMONIC_KEY, Integer.valueOf(value));
  }

  public final void setName(@NlsActions.ActionText String name) {
    putValue(Action.NAME, name);
  }

  public static final class Next extends WizardAction {

    public Next(WizardModel model) {
      super(IdeBundle.message("button.wizard.next"), model);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myModel.next();
    }
  }

  public static final class Previous extends WizardAction {

    public Previous(WizardModel model) {
      super(IdeBundle.message("button.wizard.previous"), model);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myModel.previous();
    }
  }

  public static final class Finish extends WizardAction {

    public Finish(WizardModel model) {
      super(IdeBundle.message("button.create"), model);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myModel.finish();
    }
  }

  public static final class Cancel extends WizardAction {

    public Cancel(WizardModel model) {
      super(IdeBundle.message("button.cancel"), model);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      myModel.cancel();
    }
  }


}