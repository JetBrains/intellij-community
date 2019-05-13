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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.Nullable;

public class CollectionUtil {
  @Nullable
  public static PsiClassType createSimilarCollection(@Nullable PsiType collection, Project project, PsiType... itemType) {
    if (InheritanceUtil.isInheritor(collection, "java.util.SortedSet")) {
      return createCollection(project, "java.util.SortedSet", itemType);
    }
    if (InheritanceUtil.isInheritor(collection, "java.util.LinkedHashSet")) {
      return createCollection(project, "java.util.LinkedHashSet", itemType);
    }
    if (InheritanceUtil.isInheritor(collection, CommonClassNames.JAVA_UTIL_SET)) {
      return createCollection(project, "java.util.HashSet", itemType);
    }
    if (InheritanceUtil.isInheritor(collection, "java.util.LinkedList")) {
      return createCollection(project, "java.util.LInkedList", itemType);
    }
    if (InheritanceUtil.isInheritor(collection, "java.util.Stack")) {
      return createCollection(project, "java.util.Stack", itemType);
    }
    if (InheritanceUtil.isInheritor(collection, "java.util.Vector")) {
      return createCollection(project, "java.util.Vector", itemType);
    }
    if (InheritanceUtil.isInheritor(collection, CommonClassNames.JAVA_UTIL_LIST)) {
      return createCollection(project, "java.util.ArrayList", itemType);
    }
    if (InheritanceUtil.isInheritor(collection, "java.util.Queue")) {
      return createCollection(project, "java.util.LinkedList", itemType);
    }

    return createCollection(project, "java.util.ArrayList", itemType);
  }

  @Nullable
  private static PsiClassType createCollection(Project project, String collectionName, PsiType... item) {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiClass collection =
      JavaPsiFacade.getInstance(project).findClass(collectionName, GlobalSearchScope.allScope(project));
    if (collection == null) return null;

    PsiTypeParameter[] parameters = collection.getTypeParameters();
    if (parameters.length != 1) return null;

    return factory.createType(collection, item);
  }
}
