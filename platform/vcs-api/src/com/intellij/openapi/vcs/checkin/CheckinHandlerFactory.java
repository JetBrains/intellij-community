/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * Factory which provides callbacks to run before and after checkin operations.
 *
 * @see com.intellij.openapi.vcs.ProjectLevelVcsManager#registerCheckinHandlerFactory(CheckinHandlerFactory)
 * @author lesya
 * @since 5.1
 */
public abstract class CheckinHandlerFactory {
  public static final ExtensionPointName<CheckinHandlerFactory> EP_NAME = ExtensionPointName.create("com.intellij.checkinHandlerFactory");
  
  /**
   * Creates a handler for a single Checkin Project or Checkin File operation.
   *
   * @param panel the class which can be used to retrieve information about the files to be committed,
   *              and to get or set the commit message.
   * @return the handler instance.
   */
  @NotNull
  public abstract CheckinHandler createHandler(final CheckinProjectPanel panel);
}
