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
package org.jetbrains.plugins.groovy.lang.flow.visitor;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.flow.GrDataFlowRunner;

import java.util.Collection;
import java.util.Map;

public class GrNullabilityInstructionVisitor extends GrStandardInstructionVisitor<GrNullabilityInstructionVisitor> {

  public GrNullabilityInstructionVisitor(GrDataFlowRunner<GrNullabilityInstructionVisitor> runner) {
    super(runner);
  }

  private final MultiMap<GrNullabilityProblem, PsiElement> myProblems = new MultiMap<GrNullabilityProblem, PsiElement>();
  private final Map<Pair<GrNullabilityProblem, PsiElement>, StateInfo> myStateInfos = ContainerUtil.newHashMap();

  public MultiMap<GrNullabilityProblem, PsiElement> getProblems() {
    return myProblems;
  }

  public Collection<PsiElement> getProblems(final GrNullabilityProblem kind) {
    return ContainerUtil.filter(myProblems.get(kind), new Condition<PsiElement>() {
      @Override
      public boolean value(PsiElement psiElement) {
        StateInfo info = myStateInfos.get(Pair.create(kind, psiElement));
        // non-ephemeral NPE should be reported
        // ephemeral NPE should also be reported if only ephemeral states have reached a particular problematic instruction
        //  (e.g. if it's inside "if (var == null)" check after contract method invocation
        return info.normalNpe || info.ephemeralNpe && !info.normalOk;
      }
    });
  }

  @Override
  protected void report(boolean ok, boolean ephemeral, @NotNull GrNullabilityProblem problem, @Nullable PsiElement anchor) {
    if (!ok && anchor != null) {
      myProblems.putValue(problem, anchor);
    }
    Pair<GrNullabilityProblem, PsiElement> key = Pair.create(problem, anchor);
    StateInfo info = myStateInfos.get(key);
    if (info == null) {
      myStateInfos.put(key, info = new StateInfo());
    }
    if (ephemeral && !ok) {
      info.ephemeralNpe = true;
    }
    else if (!ephemeral) {
      if (ok) {
        info.normalOk = true;
      }
      else {
        info.normalNpe = true;
      }
    }
  }

  private static class StateInfo {
    boolean ephemeralNpe;
    boolean normalNpe;
    boolean normalOk;
  }
}


