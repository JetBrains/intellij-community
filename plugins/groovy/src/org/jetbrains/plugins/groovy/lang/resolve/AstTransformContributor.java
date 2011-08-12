/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Max Medvedev
 */
public abstract class AstTransformContributor {
  private static final ExtensionPointName<AstTransformContributor> EP_NAME = ExtensionPointName.create("org.intellij.groovy.astTransformContributor");

  public abstract void getMethods(@NotNull final GrTypeDefinition clazz, Collection<PsiMethod> collector);

  private static final ThreadLocal<Set<GrTypeDefinition>> IN_PROGRESS = new ThreadLocal<Set<GrTypeDefinition>>();

  public static Collection<PsiMethod> runContributors(@NotNull final GrTypeDefinition clazz, List<PsiMethod> collector) {
    Set<GrTypeDefinition> inProgress = IN_PROGRESS.get();
    if (inProgress == null) {
      inProgress = new HashSet<GrTypeDefinition>();
      IN_PROGRESS.set(inProgress);
    }

    boolean added = inProgress.add(clazz);
    assert added;

    try {
      for (final AstTransformContributor contributor : EP_NAME.getExtensions()) {
        contributor.getMethods(clazz, collector);
      }
    }
    finally {
      inProgress.remove(clazz);
      //if (inProgress.isEmpty()) {
      //  IN_PROGRESS.remove();       Don't remove empty set, ThreadLocal automatically removes value when thread stop.
      //}
    }

    return collector;
  }
}
