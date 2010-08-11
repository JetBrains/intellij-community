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

package org.jetbrains.plugins.groovy.dsl.holders;

import com.intellij.psi.scope.PsiScopeProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ilyas
 */
public class CompoundMembersHolder implements CustomMembersHolder {

  private final List<CustomMembersHolder> myHolders = new ArrayList<CustomMembersHolder>();

  public boolean processMembers(PsiScopeProcessor processor) {
    for (CustomMembersHolder holder : myHolders) {
      if (!holder.processMembers(processor)) return false;
    }
    return true;
  }

  public synchronized void addHolder(CustomMembersHolder holder) {
    myHolders.add(holder);
  }
}
