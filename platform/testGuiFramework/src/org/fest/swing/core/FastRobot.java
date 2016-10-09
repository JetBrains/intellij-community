/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.fest.swing.core;

import org.fest.swing.hierarchy.ComponentHierarchy;
import org.fest.swing.hierarchy.ExistingHierarchy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * @author Sergey Karashevich
 */
public class FastRobot extends BasicRobot {

  public FastRobot(){
    super((Object)null, new ExistingHierarchy());
  }

  FastRobot(@Nullable Object screenLockOwner,
            @NotNull ComponentHierarchy hierarchy) {
    super(screenLockOwner, hierarchy);
  }


  @Override
  public void waitForIdle() {
    //do not wait for idle
  }

  public void superWaitForIdle(){
    super.waitForIdle();
  }

}
