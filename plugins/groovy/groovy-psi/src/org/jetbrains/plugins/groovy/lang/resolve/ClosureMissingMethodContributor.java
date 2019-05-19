// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    if (!ResolveUtilKt.shouldProcessMethods(processor)) return true;
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
