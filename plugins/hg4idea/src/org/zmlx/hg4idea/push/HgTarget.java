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
package org.zmlx.hg4idea.push;

import com.intellij.dvcs.push.PushTarget;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.util.HgUtil;

public class HgTarget implements PushTarget {
  @NotNull String myTarget;

  public HgTarget(@NotNull String name) {
    myTarget = name;
  }

  @Override
  @NotNull
  public String getPresentation() {
    return HgUtil.removePasswordIfNeeded(myTarget);
  }
}
