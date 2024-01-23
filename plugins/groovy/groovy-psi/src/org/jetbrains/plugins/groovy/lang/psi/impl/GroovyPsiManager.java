// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service(Service.Level.PROJECT)
public final class GroovyPsiManager {

  private static final Logger LOG = Logger.getInstance(GroovyPsiManager.class);

  private static final Set<String> ourPopularClasses = ContainerUtil.newHashSet(GroovyCommonClassNames.GROOVY_LANG_CLOSURE,
                                                                                GroovyCommonClassNames.GROOVY_OBJECT,
                                                                                GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT,
                                                                                GroovyCommonClassNames.GROOVY_LANG_SCRIPT,
                                                                                CommonClassNames.JAVA_UTIL_LIST,
                                                                                CommonClassNames.JAVA_UTIL_COLLECTION,
                                                                                CommonClassNames.JAVA_LANG_STRING);
  private final @NotNull Project myProject;

  private final Map<String, GrTypeDefinition> myArrayClass = new HashMap<>();

  private static final RecursionGuard<PsiElement> ourGuard = RecursionManager.createGuard("groovyPsiManager");

  public GroovyPsiManager(@NotNull Project project) {
    myProject = project;
  }

  public static GroovyPsiManager getInstance(Project project) {
    return project.getService(GroovyPsiManager.class);
  }

  public @NotNull PsiClassType createTypeByFQClassName(@NotNull String fqName, @NotNull GlobalSearchScope resolveScope) {
    if (ourPopularClasses.contains(fqName)) {
      PsiClass result = JavaPsiFacade.getInstance(myProject).findClass(fqName, resolveScope);
      if (result != null) {
        return JavaPsiFacade.getElementFactory(myProject).createType(result);
      }
    }

    return JavaPsiFacade.getElementFactory(myProject).createTypeByFQClassName(fqName, resolveScope);
  }

  public @Nullable GrTypeDefinition getArrayClass(@NotNull PsiType type) {
    final String typeText = type.getCanonicalText();
    GrTypeDefinition definition = myArrayClass.get(typeText);
    if (definition == null) {
      try {
        definition = GroovyPsiElementFactory.getInstance(myProject).createTypeDefinition(
          "class __ARRAY__ { public int length; public " + typeText + "[] clone(){} }"
        );
        myArrayClass.put(typeText, definition);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return null;
      }
    }

    return definition;
  }

  public static @Nullable PsiType inferType(@NotNull PsiElement element, @NotNull Computable<? extends PsiType> computable) {
    List<? extends PsiElement> stack = ourGuard.currentStack();
    if (stack.size() > 7) { //don't end up walking the whole project PSI
      ourGuard.prohibitResultCaching(stack.get(0));
      return null;
    }

    return ourGuard.doPreventingRecursion(element, true, computable);
  }
}
