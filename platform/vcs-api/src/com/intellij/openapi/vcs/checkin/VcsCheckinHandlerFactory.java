/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.CommitContext;
import org.jetbrains.annotations.NotNull;

public abstract class VcsCheckinHandlerFactory implements BaseCheckinHandlerFactory {
  public static final ExtensionPointName<VcsCheckinHandlerFactory> EP_NAME = ExtensionPointName.create("com.intellij.vcsCheckinHandlerFactory");

  private final VcsKey myKey;

  protected VcsCheckinHandlerFactory(@NotNull final VcsKey key) {
    myKey = key;
  }

  @NotNull
  @Override
  public CheckinHandler createHandler(@NotNull CheckinProjectPanel panel, @NotNull CommitContext commitContext) {
    if (! panel.vcsIsAffected(myKey.getName())) return CheckinHandler.DUMMY;
    return createVcsHandler(panel);
  }

  @NotNull
  protected abstract CheckinHandler createVcsHandler(CheckinProjectPanel panel);

  public VcsKey getKey() {
    return myKey;
  }

  @Override
  public BeforeCheckinDialogHandler createSystemReadyHandler(@NotNull Project project) {
    return null;
  }
}
