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
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.openapi.util.Key;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

import static org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.ResolveKind.*;

/**
 * Created by Max Medvedev on 31/03/14
 */
public abstract class GrScopeProcessorWithHints implements PsiScopeProcessor, NameHint, ClassHint, ElementClassHint {
  protected final EnumSet<ResolveKind> myResolveTargetKinds;
  protected final String myName;

  public GrScopeProcessorWithHints(@Nullable String name,
                                   @Nullable EnumSet<ResolveKind> resolveTargets) {
    myName = name;
    myResolveTargetKinds = resolveTargets;
  }

  @Override
  @SuppressWarnings({"unchecked"})
  public <T> T getHint(@NotNull Key<T> hintKey) {
    if (NameHint.KEY == hintKey && myName != null) {
      return (T)this;
    }

    if ((ClassHint.KEY == hintKey || ElementClassHint.KEY == hintKey) && myResolveTargetKinds != null) {
      return (T)this;
    }

    return null;
  }

  @Override
  public boolean shouldProcess(ResolveKind resolveKind) {
    assert myResolveTargetKinds != null : "don't invoke shouldProcess if resolveTargets are not declared";
    return myResolveTargetKinds.contains(resolveKind);
  }

  @Override
  public boolean shouldProcess(DeclarationKind kind) {
    switch (kind) {
      case CLASS:
        return shouldProcess(CLASS);

      case ENUM_CONST:
      case VARIABLE:
      case FIELD:
        return shouldProcess(PROPERTY);

      case METHOD:
        return shouldProcess(METHOD);

      case PACKAGE:
        return shouldProcess(PACKAGE);
    }

    return false;
  }

  @Override
  public String getName(@NotNull ResolveState state) {
    return myName;
  }

  public String getName() {
    return myName;
  }

  @Override
  public void handleEvent(@NotNull Event event, @Nullable Object associated) {
  }

}
