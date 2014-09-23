/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.push;

import git4idea.config.UpdateMethod;
import org.jetbrains.annotations.NotNull;

// holds settings chosen in GitRejectedPushUpdate dialog to reuse if the next push is rejected again.
class PushUpdateSettings {

  private final boolean myUpdateAllRoots;
  @NotNull private final UpdateMethod myUpdateMethod;

  PushUpdateSettings(boolean updateAllRoots, @NotNull UpdateMethod updateMethod) {
    myUpdateAllRoots = updateAllRoots;
    myUpdateMethod = updateMethod;
  }

  boolean shouldUpdateAllRoots() {
    return myUpdateAllRoots;
  }

  @NotNull
  UpdateMethod getUpdateMethod() {
    return myUpdateMethod;
  }

  @Override
  public String toString() {
    return String.format("UpdateSettings{myUpdateAllRoots=%s, myUpdateMethod=%s}", myUpdateAllRoots, myUpdateMethod);
  }
}
