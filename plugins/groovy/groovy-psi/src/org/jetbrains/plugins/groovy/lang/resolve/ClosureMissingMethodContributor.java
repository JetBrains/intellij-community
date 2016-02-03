/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

/**
 * @author Sergey Evdokimov
 */
public abstract class ClosureMissingMethodContributor {

  public static final ExtensionPointName<ClosureMissingMethodContributor> EP_NAME = ExtensionPointName.create("org.intellij.groovy.closureMissingMethodContributor");

  public static boolean processMethodsFromClosures(GrReferenceExpression ref, PsiScopeProcessor processor) {
    for (PsiElement e = ref.getContext(); e != null; e = e.getContext()) {
      if (e instanceof GrClosableBlock) {
        ResolveState state = ResolveState.initial().put(ClassHint.RESOLVE_CONTEXT, e);
        for (ClosureMissingMethodContributor contributor : EP_NAME.getExtensions()) {
          if (!contributor.processMembers((GrClosableBlock)e, processor, ref, state)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  public abstract boolean processMembers(GrClosableBlock closure, PsiScopeProcessor processor, GrReferenceExpression refExpr, ResolveState state);

}
