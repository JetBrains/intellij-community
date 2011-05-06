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

package org.jetbrains.android.exportSignedPackage;

import com.intellij.ide.wizard.CommitStepException;
import com.intellij.ide.wizard.StepAdapter;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;

/**
 * @author Eugene.Kudelevsky
 */
public abstract class ExportSignedPackageWizardStep extends StepAdapter {
  private int previousStepIndex = -1;

  public void setPreviousStepIndex(int previousStepIndex) {
    this.previousStepIndex = previousStepIndex;
  }

  public int getPreviousStepIndex() {
    return previousStepIndex;
  }

  protected boolean canFinish() {
    return false;
  }

  public abstract String getHelpId();

  protected static void checkNewPassword(JPasswordField passwordField, JPasswordField confirmedPasswordField) throws CommitStepException {
    char[] password = passwordField.getPassword();
    char[] confirmedPassword = confirmedPasswordField.getPassword();
    try {
      checkPassword(password);
      if (password.length < 6) {
        throw new CommitStepException(AndroidBundle.message("android.export.package.incorrect.password.length"));
      }
      if (!Arrays.equals(password, confirmedPassword)) {
        throw new CommitStepException(AndroidBundle.message("android.export.package.passwords.not.match.error"));
      }
    }
    finally {
      Arrays.fill(password, '\0');
      Arrays.fill(confirmedPassword, '\0');
    }
  }

  protected abstract void commitForNext() throws CommitStepException;

  protected static void checkPassword(char[] password) throws CommitStepException {
    if (password.length == 0) {
        throw new CommitStepException(AndroidBundle.message("android.export.package.specify.password.error"));
      }
  }

  protected static void checkPassword(JPasswordField passwordField) throws CommitStepException {
    char[] password = passwordField.getPassword();
    try {
      checkPassword(password);
    }
    finally {
      Arrays.fill(password, '\0');
    }
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Nullable
  protected JComponent getPreferredFocusedComponent() {
    return null;
  }
}
