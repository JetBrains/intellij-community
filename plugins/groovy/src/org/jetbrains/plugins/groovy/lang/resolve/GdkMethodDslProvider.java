/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiMethod;
import com.intellij.util.Function;
import org.jetbrains.plugins.groovy.dsl.GdslMembersHolderConsumer;
import org.jetbrains.plugins.groovy.dsl.dsltop.GdslMembersProvider;
import org.jetbrains.plugins.groovy.dsl.holders.DelegatedMembersHolder;
import org.jetbrains.plugins.groovy.dsl.toplevel.CategoryMethodProvider;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl;

import java.util.List;

/**
 * @author Maxim.Medvedev
 */
@SuppressWarnings({"MethodMayBeStatic"})
public class GdkMethodDslProvider implements GdslMembersProvider {
  public void category(String className, GdslMembersHolderConsumer consumer) {
    processCategoryMethods(className, consumer, new Function<PsiMethod, PsiMethod>() {
      public PsiMethod fun(PsiMethod m) {
        return new GrGdkMethodImpl(m, false);
      }
    });
  }

  public void category(String className, final boolean isStatic, GdslMembersHolderConsumer consumer) {
    processCategoryMethods(className, consumer, new Function<PsiMethod, PsiMethod>() {
      public PsiMethod fun(PsiMethod param) {
        return new GrGdkMethodImpl(param, isStatic);
      }
    });
  }

  public void category(String className, Function<PsiMethod, PsiMethod> converter, GdslMembersHolderConsumer consumer) {
    processCategoryMethods(className, consumer, converter);
  }

  public static void processCategoryMethods(String className, GdslMembersHolderConsumer consumer, Function<PsiMethod, PsiMethod> converter) {
    if (consumer.getPsiType() == null) return;
    List<PsiMethod> methods =
      CategoryMethodProvider.provideMethods(consumer.getPsiType(), consumer.getProject(), className, consumer.getResolveScope(), converter);
    final DelegatedMembersHolder holder = new DelegatedMembersHolder();
    for (PsiMethod m : methods) {
      holder.addMember(m);
    }
    consumer.addMemberHolder(holder);
  }
}
