/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.google.common.collect.Sets;
import com.intellij.ProjectTopics;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.reference.SoftReference;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * @author ven
 */
public class GroovyPsiManager {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager");
  private static final Set<String> ourPopularClasses = Sets.newHashSet(GroovyCommonClassNames.GROOVY_LANG_CLOSURE,
                                                                       GroovyCommonClassNames.DEFAULT_BASE_CLASS_NAME,
                                                                       GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT,
                                                                       CommonClassNames.JAVA_UTIL_LIST,
                                                                       CommonClassNames.JAVA_UTIL_COLLECTION,
                                                                       CommonClassNames.JAVA_LANG_STRING);
  private final Project myProject;

  private volatile GrTypeDefinition myArrayClass;

  private final ConcurrentMap<GroovyPsiElement, PsiType> myCalculatedTypes = new ConcurrentWeakHashMap<GroovyPsiElement, PsiType>();
  private final ConcurrentMap<String, SoftReference<Map<GlobalSearchScope, PsiClass>>> myClassCache = new ConcurrentHashMap<String, SoftReference<Map<GlobalSearchScope, PsiClass>>>();

  private static final RecursionGuard ourGuard = RecursionManager.createGuard("groovyPsiManager");

  public GroovyPsiManager(Project project) {
    myProject = project;

    ((PsiManagerEx)PsiManager.getInstance(myProject)).registerRunnableToRunOnAnyChange(new Runnable() {
      public void run() {
        dropTypesCache();
      }
    });
    ((PsiManagerEx)PsiManager.getInstance(myProject)).registerRunnableToRunOnChange(new Runnable() {
      public void run() {
        myClassCache.clear();
      }
    });

    myProject.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        dropTypesCache();
        myClassCache.clear();
      }
    });
  }

  public void dropTypesCache() {
    myCalculatedTypes.clear();
  }

  public static boolean isInheritorCached(@Nullable PsiClass aClass, @NotNull String baseClassName) {
    if (aClass == null) return false;

    return InheritanceUtil.isInheritorOrSelf(aClass, getInstance(aClass.getProject()).findClassWithCache(baseClassName, aClass.getResolveScope()), true);
  }

  public static boolean isInheritorCached(@Nullable PsiType type, @NotNull String baseClassName) {
    if (type instanceof PsiClassType) {
      return isInheritorCached(((PsiClassType)type).resolve(), baseClassName);
    }
    return false;
  }

  public static GroovyPsiManager getInstance(Project project) {
    return ServiceManager.getService(project, GroovyPsiManager.class);
  }

  public PsiClassType createTypeByFQClassName(String fqName, GlobalSearchScope resolveScope) {
    if (ourPopularClasses.contains(fqName)) {
      PsiClass result = findClassWithCache(fqName, resolveScope);
      if (result != null) {
        return JavaPsiFacade.getElementFactory(myProject).createType(result);
      }
    }

    return JavaPsiFacade.getElementFactory(myProject).createTypeByFQClassName(fqName, resolveScope);
  }

  @Nullable
  public PsiClass findClassWithCache(String fqName, GlobalSearchScope resolveScope) {
    SoftReference<Map<GlobalSearchScope, PsiClass>> reference = myClassCache.get(fqName);
    Map<GlobalSearchScope, PsiClass> map = reference == null ? null : reference.get();
    if (map == null) {
      map = new ConcurrentHashMap<GlobalSearchScope, PsiClass>();
      myClassCache.put(fqName, new SoftReference<Map<GlobalSearchScope, PsiClass>>(map));
    }
    PsiClass cached = map.get(resolveScope);
    if (cached != null) {
      return cached;
    }

    PsiClass result = JavaPsiFacade.getInstance(myProject).findClass(fqName, resolveScope);
    if (result != null) {
      map.put(resolveScope, result);
    }
    return result;
  }

  @Nullable
  public <T extends GroovyPsiElement> PsiType getType(T element, Function<T, PsiType> calculator) {
    PsiType type = myCalculatedTypes.get(element);
    if (type == null) {
      RecursionGuard.StackStamp stamp = ourGuard.markStack();
      type = calculator.fun(element);
      if (type == null) {
        type = PsiType.NULL;
      }
      if (stamp.mayCacheNow()) {
        type = ConcurrencyUtil.cacheOrGet(myCalculatedTypes, element, type);
      } else {
        final PsiType alreadyInferred = myCalculatedTypes.get(element);
        if (alreadyInferred != null) {
          type = alreadyInferred;
        }
      }
    }
    if (!type.isValid()) {
      LOG.error("Type is invalid: " + type + "; element: " + element + " of class " + element.getClass());
    }
    return PsiType.NULL.equals(type) ? null : type;
  }

  public GrTypeDefinition getArrayClass() {
    if (myArrayClass == null) {
      try {
        myArrayClass = GroovyPsiElementFactory.getInstance(myProject).createTypeDefinition("class __ARRAY__ { public int length }");
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    return myArrayClass;
  }

  @Nullable
  public static PsiType inferType(PsiElement element, Computable<PsiType> computable) {
    List<Object> stack = ourGuard.currentStack();
    if (stack.size() > 7) { //don't end up walking the whole project PSI
      ourGuard.prohibitResultCaching(stack.get(0));
      return null;
    }

    return ourGuard.doPreventingRecursion(element, computable);
  }

}
