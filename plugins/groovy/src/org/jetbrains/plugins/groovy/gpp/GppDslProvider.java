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
package org.jetbrains.plugins.groovy.gpp;

import com.intellij.psi.PsiMethod;
import com.intellij.util.Function;
import org.jetbrains.plugins.groovy.dsl.GdslMembersHolderConsumer;
import org.jetbrains.plugins.groovy.dsl.dsltop.GdslMembersProvider;
import org.jetbrains.plugins.groovy.lang.resolve.GdkMethodDslProvider;

/**
 * @author Maxim.Medvedev
 */
@SuppressWarnings({"MethodMayBeStatic"})
public class GppDslProvider implements GdslMembersProvider {
  public void gppCategory(String className, GdslMembersHolderConsumer consumer) {
    gppCategory(className, false, consumer);
  }
  public void gppCategory(String className, boolean isStatic, GdslMembersHolderConsumer consumer) {
    Function<PsiMethod, PsiMethod> staticConverter = new Function<PsiMethod, PsiMethod>() {
      public PsiMethod fun(PsiMethod m) {
        return new GppGdkMethod(m, true);
      }
    };
    Function<PsiMethod, PsiMethod> nonStaticConverter = new Function<PsiMethod, PsiMethod>() {
      public PsiMethod fun(PsiMethod m) {
        return new GppGdkMethod(m, false);
      }
    };
    GdkMethodDslProvider.processCategoryMethods(className, consumer, isStatic ? staticConverter : nonStaticConverter);
  }
}
