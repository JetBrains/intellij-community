// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl.holders;

import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.dsl.ClosureDescriptor;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author ilyas
 */
class CompoundMembersHolder implements CustomMembersHolder {

  private final List<CustomMembersHolder> myHolders;

  CompoundMembersHolder(@NotNull List<CustomMembersHolder> holders) {
    myHolders = new ArrayList<>(holders);
  }

  @Override
  public boolean processMembers(GroovyClassDescriptor descriptor, PsiScopeProcessor processor, ResolveState state) {
    for (CustomMembersHolder holder : myHolders) {
      if (!holder.processMembers(descriptor, processor, state)) return false;
    }
    return true;
  }

  @Override
  public void consumeClosureDescriptors(GroovyClassDescriptor descriptor, Consumer<? super ClosureDescriptor> consumer) {
    for (CustomMembersHolder holder : myHolders) {
      holder.consumeClosureDescriptors(descriptor, consumer);
    }
  }
}
