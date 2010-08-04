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
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ConcurrentWeakHashMap;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.stubs.GroovyShortNamesCache;

import java.util.*;

/**
 * @author ven
 */
public class GroovyPsiManager {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager");
  private final Project myProject;

  private GrTypeDefinition myArrayClass;

  private final ConcurrentWeakHashMap<GroovyPsiElement, PsiType> myCalculatedTypes = new ConcurrentWeakHashMap<GroovyPsiElement, PsiType>();
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
      }
    });
  }

  public TypeInferenceHelper getTypeInferenceHelper() {
    return myTypeInferenceHelper;
  }

  public void dropTypesCache() {
    myCalculatedTypes.clear();
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
