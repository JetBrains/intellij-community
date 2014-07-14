/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.SingletonInstancesCache;
import org.jetbrains.plugins.groovy.extensions.GroovyMethodDescriptor;
import org.jetbrains.plugins.groovy.extensions.GroovyMethodInfo;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.List;

/**
 * @author Sergey Evdokimov
 */
public class PluginXmlClosureMemberContributor extends ClosureMissingMethodContributor {
  @Override
  public boolean processMembers(GrClosableBlock closure, PsiScopeProcessor processor, GrReferenceExpression refExpr, ResolveState state) {
    PsiElement parent = closure.getParent();
    if (parent instanceof GrArgumentList) parent = parent.getParent();

    if (!(parent instanceof GrMethodCall)) return true;

    PsiMethod psiMethod = ((GrMethodCall)parent).resolveMethod();
    if (psiMethod == null) return true;

    List<GroovyMethodInfo> infos = GroovyMethodInfo.getInfos(psiMethod);
    if (infos.isEmpty()) return true;

    int index = PsiUtil.getArgumentIndex((GrMethodCall)parent, closure);
    if (index == -1) return true;

    for (GroovyMethodInfo info : infos) {
      GroovyMethodDescriptor.ClosureArgument[] closureArguments = info.getDescriptor().myClosureArguments;
      if (closureArguments != null) {
        for (GroovyMethodDescriptor.ClosureArgument argument : closureArguments) {
          if (argument.index == index && argument.methodContributor != null) {
            ClosureMissingMethodContributor instance = SingletonInstancesCache.getInstance(argument.methodContributor, info.getPluginClassLoader());
            if (!instance.processMembers(closure, processor, refExpr, state)) return false;
          }
        }
      }
    }

    return true;
  }
}
