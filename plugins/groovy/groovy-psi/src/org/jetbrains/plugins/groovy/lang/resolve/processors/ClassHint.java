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
import com.intellij.psi.PsiElement;

import java.util.EnumSet;

/**
 * @author ven
 */
public interface ClassHint {
  Key<ClassHint> KEY = Key.create("ClassHint");
  Key<PsiElement> RESOLVE_CONTEXT = Key.create("RESOLVE_CONTEXT");
  EnumSet<ResolveKind> RESOLVE_KINDS_CLASS_PACKAGE = EnumSet.of(ResolveKind.CLASS, ResolveKind.PACKAGE);
  EnumSet<ResolveKind> RESOLVE_KINDS_CLASS = EnumSet.of(ResolveKind.CLASS);
  EnumSet<ResolveKind> RESOLVE_KINDS_METHOD = EnumSet.of(ResolveKind.METHOD);
  EnumSet<ResolveKind> RESOLVE_KINDS_METHOD_PROPERTY = EnumSet.of(ResolveKind.METHOD, ResolveKind.PROPERTY);
  EnumSet<ResolveKind> RESOLVE_KINDS_PROPERTY = EnumSet.of(ResolveKind.PROPERTY);

  enum ResolveKind {
    CLASS,
    PACKAGE,
    METHOD,
    PROPERTY
  }

  boolean shouldProcess(ResolveKind resolveKind);
}
