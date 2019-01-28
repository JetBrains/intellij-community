// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl.holders;

import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;
import org.jetbrains.plugins.groovy.lang.completion.closureParameters.ClosureDescriptor;

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
    public void consumeClosureDescriptors(GroovyClassDescriptor descriptor,
                                          Consumer<? super ClosureDescriptor> consumer) {}
  };

  boolean processMembers(GroovyClassDescriptor descriptor, PsiScopeProcessor processor, ResolveState state);

  void consumeClosureDescriptors(GroovyClassDescriptor descriptor, Consumer<? super ClosureDescriptor> consumer);
}
