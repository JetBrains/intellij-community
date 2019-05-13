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

package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.changes.CommitContext;
import org.jetbrains.annotations.NotNull;

/**
 * Factory which provides callbacks to run before and after checkin operations.
 * !! This factory is loaded on first commit
 * should NOT be used from VCS plugins
 * use {@link VcsCheckinHandlerFactory} implementations instead, define through EP "com.intellij.checkinHandlerFactory"
 * they would automatically would be registered in {@link com.intellij.openapi.vcs.AbstractVcs#activate()}
 * and unregistered in {@link com.intellij.openapi.vcs.AbstractVcs#deactivate()}
 *
 * @author lesya
 * @since 5.1
 */
public abstract class CheckinHandlerFactory implements BaseCheckinHandlerFactory {
  public static final ExtensionPointName<CheckinHandlerFactory> EP_NAME = ExtensionPointName.create("com.intellij.checkinHandlerFactory");

  /**
   * Creates a handler for a single Checkin Project or Checkin File operation.
   *
   * @param panel         the class which can be used to retrieve information about the files to be committed,
   *                      and to get or set the commit message.
   * @return the handler instance.
   */
  @Override
  @NotNull
  public abstract CheckinHandler createHandler(@NotNull CheckinProjectPanel panel, @NotNull CommitContext commitContext);

  @Override
  public BeforeCheckinDialogHandler createSystemReadyHandler(@NotNull Project project) {
    return null;
  }
}
