// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.util.VolatileNotNullLazyValue;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.dgm.GdkMethodHolder;
import org.jetbrains.plugins.groovy.dsl.GdslMembersHolderConsumer;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;
import org.jetbrains.plugins.groovy.dsl.dsltop.GdslMembersProvider;
import org.jetbrains.plugins.groovy.dsl.holders.CustomMembersHolder;
import org.jetbrains.plugins.groovy.lang.completion.closureParameters.ClosureDescriptor;
import org.jetbrains.plugins.groovy.lang.resolve.processors.MultiProcessor;

import java.util.function.Consumer;

/**
 * @author Maxim.Medvedev
 */
public class GdkMethodDslProvider implements GdslMembersProvider {

  public void category(String className, GdslMembersHolderConsumer consumer) {
    processCategoryMethods(className, consumer, false);
  }

  public void category(String className, final boolean isStatic, GdslMembersHolderConsumer consumer) {
    processCategoryMethods(className, consumer, isStatic);
  }

  private static void processCategoryMethods(final String className, final GdslMembersHolderConsumer consumer, final boolean isStatic) {
    final GlobalSearchScope scope = consumer.getResolveScope();
    final PsiClass categoryClass = JavaPsiFacade.getInstance(consumer.getProject()).findClass(className, scope);
    if (categoryClass == null) {
      return;
    }

    final VolatileNotNullLazyValue<GdkMethodHolder> methodsMap = new VolatileNotNullLazyValue<GdkMethodHolder>() {
      @NotNull
      @Override
      protected GdkMethodHolder compute() {
        return GdkMethodHolder.getHolderForClass(categoryClass, isStatic);
      }
    };

    consumer.addMemberHolder(new CustomMembersHolder() {

      @Override
      public boolean processMembers(GroovyClassDescriptor descriptor, PsiScopeProcessor _processor, ResolveState state) {
        for (PsiScopeProcessor each : MultiProcessor.allProcessors(_processor)) {
          if (ResolveUtil.shouldProcessMethods(each.getHint(ElementClassHint.KEY)) &&
              !methodsMap.getValue().processMethods(each, state, descriptor.getPsiType(), descriptor.getProject())) {
            return false;
          }
        }
        return true;
      }

      @Override
      public void consumeClosureDescriptors(GroovyClassDescriptor descriptor, Consumer<? super ClosureDescriptor> consumer) {}
    });
  }
}
