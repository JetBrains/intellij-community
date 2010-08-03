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

import com.intellij.ProjectTopics;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.dsl.GroovyDslExecutor;
import org.jetbrains.plugins.groovy.dsl.GroovyDslFileIndex;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl;
import org.jetbrains.plugins.groovy.lang.stubs.GroovyShortNamesCache;

import java.util.*;

/**
 * @author ven
 */
public class GroovyPsiManager {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager");
  private final Project myProject;
  private Map<String, List<PsiMethod>> myDefaultMethods;

  private GrTypeDefinition myArrayClass;

  private final ConcurrentWeakHashMap<GroovyPsiElement, PsiType> myCalculatedTypes = new ConcurrentWeakHashMap<GroovyPsiElement, PsiType>();
  private volatile boolean myRebuildGdkPending = true;
  private final GroovyShortNamesCache myCache;

  private final TypeInferenceHelper myTypeInferenceHelper;

  private static final String SYNTHETIC_CLASS_TEXT = "class __ARRAY__ { public int length }";

  public GroovyPsiManager(Project project) {
    myProject = project;
    myCache = ContainerUtil.findInstance(project.getExtensions(PsiShortNamesCache.EP_NAME), GroovyShortNamesCache.class);

    ((PsiManagerEx)PsiManager.getInstance(myProject)).registerRunnableToRunOnAnyChange(new Runnable() {
      public void run() {
        dropTypesCache();
      }
    });

    myTypeInferenceHelper = new TypeInferenceHelper(myProject);

    myProject.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        dropTypesCache();
        myRebuildGdkPending = true;
      }
    });
  }

  public TypeInferenceHelper getTypeInferenceHelper() {
    return myTypeInferenceHelper;
  }

  public void dropTypesCache() {
    myCalculatedTypes.clear();
  }


   public List<PsiMethod> getDefaultMethods(String qName) {
    if (myRebuildGdkPending) {
      final Map<String, List<PsiMethod>> gdk = buildGDK();
      if (myRebuildGdkPending) {
        myDefaultMethods = gdk;
        myRebuildGdkPending = false;
      }
    }

    List<PsiMethod> methods = myDefaultMethods.get(qName);
    if (methods == null) return Collections.emptyList();
    return methods;
  }

  private Map<String, List<PsiMethod>> buildGDK() {
    return null;  //To change body of created methods use File | Settings | File Templates.
  }

  public List<PsiMethod> getDefaultMethods(PsiClass psiClass) {
    List<PsiMethod> list = new ArrayList<PsiMethod>();
    getDefaultMethodsInner(psiClass, new HashSet<PsiClass>(), list);
    return list;
  }

  public void getDefaultMethodsInner(PsiClass psiClass, Set<PsiClass> watched, List<PsiMethod> methods) {
    if (watched.contains(psiClass)) return;
    watched.add(psiClass);
    methods.addAll(getDefaultMethods(psiClass.getQualifiedName()));
    for (PsiClass aClass : psiClass.getSupers()) {
      getDefaultMethodsInner(aClass, watched, methods);
    }
  }

  public void addCategoryMethods(String fromClass, Map<String, List<PsiMethod>> toMap, NotNullFunction<PsiMethod, PsiMethod> converter) {
    PsiClass categoryClass = JavaPsiFacade.getInstance(myProject).findClass(fromClass, GlobalSearchScope.allScope(myProject));
    if (categoryClass != null) {
      for (PsiMethod method : categoryClass.getMethods()) {
        if (method.isConstructor()) continue;
        if (!method.hasModifierProperty(PsiModifier.STATIC) || !method.hasModifierProperty(PsiModifier.PUBLIC)) continue;
        addDefaultMethod(method, toMap, converter);
      }
    }
  }

  private static void addDefaultMethod(PsiMethod method, Map<String, List<PsiMethod>> map, NotNullFunction<PsiMethod, PsiMethod> converter) {
    if (!method.hasModifierProperty(PsiModifier.PUBLIC)) return;

    PsiParameter[] parameters = method.getParameterList().getParameters();
    LOG.assertTrue(parameters.length > 0, method.getName());
    PsiType thisType = TypeConversionUtil.erasure(parameters[0].getType());
    String thisCanonicalText = thisType.getCanonicalText();
    LOG.assertTrue(thisCanonicalText != null);
    List<PsiMethod> hisMethods = map.get(thisCanonicalText);
    if (hisMethods == null) {
      hisMethods = new ArrayList<PsiMethod>();
      map.put(thisCanonicalText, hisMethods);
    }
    hisMethods.add(converter.fun(method));
  }

  public static GroovyPsiManager getInstance(Project project) {
    return ServiceManager.getService(project, GroovyPsiManager.class);
  }

  @Nullable
  public <T extends GroovyPsiElement> PsiType getType(T element, Function<T, PsiType> calculator) {
    PsiType type = myCalculatedTypes.get(element);
    if (type == null) {
      type = calculator.fun(element);
      if (type == null) {
        type = PsiType.NULL;
      }
      type = ConcurrencyUtil.cacheOrGet(myCalculatedTypes, element, type);
    }
    if (!type.isValid()) {
      LOG.error("Type is invalid: " + type + "; element: " + element + " of class " + element.getClass());
    }
    return PsiType.NULL.equals(type) ? null : type;
  }

  public GrTypeDefinition getArrayClass() {
    if (myArrayClass == null) {
      try {
        myArrayClass = GroovyPsiElementFactory.getInstance(myProject).createTypeDefinition(SYNTHETIC_CLASS_TEXT);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    return myArrayClass;
  }

  private static final ThreadLocal<List<PsiElement>> myElementsWithTypesBeingInferred = new ThreadLocal<List<PsiElement>>() {
    protected List<PsiElement> initialValue() {
      return new ArrayList<PsiElement>();
    }
  };

  public static PsiType inferType(PsiElement element, Computable<PsiType> computable) {
    final List<PsiElement> curr = myElementsWithTypesBeingInferred.get();
    try {
      curr.add(element);
      return computable.compute();
    }
    finally {
      curr.remove(element);
    }
  }

  public static boolean isTypeBeingInferred(PsiElement element) {
    return myElementsWithTypesBeingInferred.get().contains(element);
  }

  public GroovyShortNamesCache getNamesCache() {
    return myCache;
  }
}
