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
package org.jetbrains.plugins.groovy.codeInspection.control.finalVar;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiVariable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DFAEngine;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.Semilattice;
import org.jetbrains.plugins.groovy.util.LightCacheKey;

import java.util.ArrayList;
import java.util.Map;

/**
 * @author Max Medvedev
 */
public class VariableInitializationChecker {
  private static final LightCacheKey<Map<GroovyPsiElement, Boolean>> KEY = LightCacheKey.createByFileModificationCount();

  public static boolean isVariableDefinitelyInitialized(@NotNull String varName, @NotNull Instruction[] controlFlow) {
    DFAEngine<Data> engine = new DFAEngine<>(controlFlow, new MyDfaInstance(varName), new MySemilattice());
    final ArrayList<Data> result = engine.performDFAWithTimeout();
    if (result == null) return false;

    return result.get(controlFlow.length - 1).get();
  }

  public static boolean isVariableDefinitelyInitializedCached(@NotNull PsiVariable var,
                                                              @NotNull GroovyPsiElement context,
                                                              @NotNull Instruction[] controlFlow) {
    Map<GroovyPsiElement, Boolean> map = KEY.getCachedValue(var);
    if (map == null) {
      map = ContainerUtil.newHashMap();
      KEY.putCachedValue(var, map);
    }

    final Boolean cached = map.get(context);
    if (cached != null) return cached.booleanValue();

    final boolean result = isVariableDefinitelyInitialized(var.getName(), controlFlow);
    map.put(context, result);

    return result;
  }

  private static class MyDfaInstance implements DfaInstance<Data> {
    public MyDfaInstance(String var) {
      myVar = var;
    }

    @Override
    public void fun(Data e, Instruction instruction) {
      if (instruction instanceof ReadWriteVariableInstruction &&
          ((ReadWriteVariableInstruction)instruction).getVariableName().equals(myVar)) {
        e.set(true);
      }
    }

    @NotNull
    @Override
    public Data initial() {
      return new Data(false);
    }

    @Override
    public boolean isForward() {
      return true;
    }

    private final String myVar;
  }

  private static class MySemilattice implements Semilattice<Data> {
    @NotNull
    @Override
    public Data join(@NotNull ArrayList<Data> ins) {
      if (ins.isEmpty()) return new Data(false);

      boolean b = true;
      for (Data data : ins) {
        b &= data.get().booleanValue();
      }

      return new Data(b);
    }

    @Override
    public boolean eq(Data e1, Data e2) {
      return e1.get().booleanValue() == e2.get().booleanValue();
    }
  }

  private static class Data extends Ref<Boolean> {
    public Data(Boolean value) {
      super(value);
    }
  }
}
