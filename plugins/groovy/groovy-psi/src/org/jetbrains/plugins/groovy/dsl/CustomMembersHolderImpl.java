// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FList;
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder;
import org.jetbrains.plugins.groovy.dsl.holders.NonCodeMembersHolder;

import java.util.function.Consumer;

final class CustomMembersHolderImpl implements CustomMembersHolder {

  private final FList<Descriptor> myDeclarations;

  CustomMembersHolderImpl(FList<Descriptor> declarations) {
    myDeclarations = declarations;
  }

  @Override
  public boolean processMembers(GroovyClassDescriptor descriptor, PsiScopeProcessor processor, ResolveState state) {
    return NonCodeMembersHolder.generateMembers(ContainerUtil.reverse(myDeclarations), descriptor.justGetPlaceFile())
      .processMembers(descriptor, processor, state);
  }

  @Override
  public void consumeClosureDescriptors(GroovyClassDescriptor descriptor, Consumer<? super ClosureDescriptor> consumer) {
    NonCodeMembersHolder.generateMembers(ContainerUtil.reverse(myDeclarations), descriptor.justGetPlaceFile())
      .consumeClosureDescriptors(descriptor, consumer);
  }
}
