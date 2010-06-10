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
package org.zmlx.hg4idea;

import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.checkin.CheckinHandler;
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory;
import org.jetbrains.annotations.NotNull;

/**
 * HgRemoveCheckinHandlerFactory provides the {@link CheckinHandler} which scans
 * the changes list for files, which were deleted on the file system, but not from
 * the VCS. 
 *
 * @author Kirill Likhodedov
 */
public class HgRemoveCheckinHandlerFactory extends CheckinHandlerFactory {

  @NotNull
  @Override
  public CheckinHandler createHandler(final CheckinProjectPanel checkinPanel) {
    return new HgRemoveCheckinHandler(checkinPanel);
  }

}
