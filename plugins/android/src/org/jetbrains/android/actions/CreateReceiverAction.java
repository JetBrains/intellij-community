/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.android.actions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.android.dom.manifest.ApplicationComponent;
import org.jetbrains.android.dom.manifest.Application;
import org.jetbrains.android.dom.manifest.Receiver;
import org.jetbrains.android.util.AndroidBundle;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;

/**
 * @author coyote
 */
public class CreateReceiverAction extends CreateComponentAction {
  public CreateReceiverAction() {
    super(AndroidBundle.message("create.receiver.text"), AndroidBundle.message("create.receiver.description"));
  }

  @NotNull
  protected String getClassName() {
    return "android.content.BroadcastReceiver";
  }

  protected ApplicationComponent addToManifest(@NotNull PsiClass aClass, @NotNull Application application) {
    Receiver receiver = application.addReceiver();
    receiver.getReceiverClass().setValue(aClass);
    return receiver;
  }

  protected String getErrorTitle() {
    return "Cannot create broadcast receiver";
  }

  protected String getCommandName() {
    return "Create Broadcast Receiver";
  }

  protected String getActionName(PsiDirectory directory, String newName) {
    return "Creating broadcast receiver " + newName;
  }
}
