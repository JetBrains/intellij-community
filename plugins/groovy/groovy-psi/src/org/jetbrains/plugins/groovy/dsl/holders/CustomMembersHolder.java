// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl.holders;

import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.dsl.ClosureDescriptor;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;

import java.util.List;
import java.util.function.Consumer;

/**
 * @author ilyas
 */
public interface CustomMembersHolder {

  CustomMembersHolder EMPTY = new CustomMembersHolder() {
    @Override
    public boolean processMembers(GroovyClassDescriptor descriptor, PsiScopeProcessor processor, ResolveState state) {
      return true;
    }

    @Override
    public void consumeClosureDescriptors(GroovyClassDescriptor descriptor, Consumer<? super ClosureDescriptor> consumer) {}
  };

  boolean processMembers(GroovyClassDescriptor descriptor, PsiScopeProcessor processor, ResolveState state);

  void consumeClosureDescriptors(GroovyClassDescriptor descriptor, Consumer<? super ClosureDescriptor> consumer);

  static @NotNull CustomMembersHolder create(@NotNull List<CustomMembersHolder> holders) {
    List<CustomMembersHolder> nonEmptyHolders = ContainerUtil.filter(holders, it -> it != EMPTY);
    if (nonEmptyHolders.isEmpty()) {
      return EMPTY;
    }
    else if (nonEmptyHolders.size() == 1) {
      return nonEmptyHolders.get(0);
    }
    else {
      return new CompoundMembersHolder(nonEmptyHolders);
    }
  }
}
