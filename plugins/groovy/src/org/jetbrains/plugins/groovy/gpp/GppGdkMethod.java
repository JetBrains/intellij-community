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
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.resolve.DominanceAwareMethod;

/**
* @author Maxim.Medvedev
*/
public class GppGdkMethod extends GrGdkMethodImpl implements DominanceAwareMethod {
  public GppGdkMethod(PsiMethod method, final boolean isStatic) {
    super(method, isStatic);
  }

  public boolean isMoreConcreteThan(@NotNull final PsiSubstitutor substitutor,
                           @NotNull PsiMethod another,
                           @NotNull PsiSubstitutor anotherSubstitutor,
                           @NotNull GroovyPsiElement context) {
    if (another instanceof GrGdkMethodImpl && another.getName().equals(getName())) {
      final PsiParameter[] plusParameters = getParameterList().getParameters();
      final PsiParameter[] defParameters = another.getParameterList().getParameters();

      final PsiType[] paramTypes = new PsiType[plusParameters.length];
      for (int i = 0; i < paramTypes.length; i++) {
        paramTypes[i] = eliminateOneMethodInterfaces(plusParameters[i], defParameters, i);

      }

      final GrClosureSignature gdkSignature = GrClosureSignatureUtil.createSignature(another, anotherSubstitutor);
      if (GrClosureSignatureUtil.isSignatureApplicable(gdkSignature, paramTypes, context)) {
        return true;
      }
    }
    return false;
  }

  private static PsiType eliminateOneMethodInterfaces(PsiParameter plusParameter, PsiParameter[] gdkParameters, int i) {
    PsiType type = plusParameter.getType();
    if (i < gdkParameters.length &&
        gdkParameters[i].getType().equalsToText(GrClosableBlock.GROOVY_LANG_CLOSURE) &&
        GppClosureParameterTypeProvider.findSingleAbstractMethodSignature(type) != null) {
      return gdkParameters[i].getType();
    }
    return type;
  }
}
