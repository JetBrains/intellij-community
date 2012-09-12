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
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

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

  protected abstract void commitForNext() throws CommitStepException;

  @Override
  public Icon getIcon() {
    return null;
  }
}
