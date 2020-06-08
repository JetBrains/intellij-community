// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.Nullable;

public final class CollectionUtil {
  @Nullable
  public static PsiClassType createSimilarCollection(@Nullable PsiType collection, Project project, PsiType... itemType) {
    if (InheritanceUtil.isInheritor(collection, CommonClassNames.JAVA_UTIL_SORTED_SET)) {
      return createCollection(project, CommonClassNames.JAVA_UTIL_SORTED_SET, itemType);
    }
    if (InheritanceUtil.isInheritor(collection, CommonClassNames.JAVA_UTIL_LINKED_HASH_SET)) {
      return createCollection(project, CommonClassNames.JAVA_UTIL_LINKED_HASH_SET, itemType);
    }
    if (InheritanceUtil.isInheritor(collection, CommonClassNames.JAVA_UTIL_SET)) {
      return createCollection(project, "java.util.HashSet", itemType);
    }
    if (InheritanceUtil.isInheritor(collection, CommonClassNames.JAVA_UTIL_LINKED_LIST)) {
      return createCollection(project, CommonClassNames.JAVA_UTIL_LINKED_LIST, itemType);
    }
    if (InheritanceUtil.isInheritor(collection, CommonClassNames.JAVA_UTIL_STACK)) {
      return createCollection(project, CommonClassNames.JAVA_UTIL_STACK, itemType);
    }
    if (InheritanceUtil.isInheritor(collection, "java.util.Vector")) {
      return createCollection(project, "java.util.Vector", itemType);
    }
    if (InheritanceUtil.isInheritor(collection, CommonClassNames.JAVA_UTIL_LIST)) {
      return createCollection(project, "java.util.ArrayList", itemType);
    }
    if (InheritanceUtil.isInheritor(collection, CommonClassNames.JAVA_UTIL_QUEUE)) {
      return createCollection(project, CommonClassNames.JAVA_UTIL_LINKED_LIST, itemType);
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
