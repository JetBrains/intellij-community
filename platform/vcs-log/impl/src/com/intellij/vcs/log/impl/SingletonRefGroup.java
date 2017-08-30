/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log.impl;

import com.intellij.vcs.log.RefGroup;
import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * {@link RefGroup} containing only one {@link VcsRef}.
 */
public class SingletonRefGroup implements RefGroup {
  private final VcsRef myRef;

  public SingletonRefGroup(VcsRef ref) {
    myRef = ref;
  }

  @Override
  public boolean isExpanded() {
    return false;
  }

  @NotNull
  @Override
  public String getName() {
    return myRef.getName();
  }

  @NotNull
  @Override
  public List<VcsRef> getRefs() {
    return Collections.singletonList(myRef);
  }

  @NotNull
  @Override
  public List<Color> getColors() {
    return Collections.singletonList(myRef.getType().getBackgroundColor());
  }
}
