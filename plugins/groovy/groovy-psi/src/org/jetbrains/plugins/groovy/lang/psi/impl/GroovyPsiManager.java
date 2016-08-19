/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.RecursionGuard;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * @author ven
 */
public class GroovyPsiManager {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager");
  private static final Set<String> ourPopularClasses = ContainerUtil.newHashSet(GroovyCommonClassNames.GROOVY_LANG_CLOSURE,
                                                                                GroovyCommonClassNames.DEFAULT_BASE_CLASS_NAME,
                                                                                GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT,
                                                                                GroovyCommonClassNames.GROOVY_LANG_SCRIPT,
                                                                                CommonClassNames.JAVA_UTIL_LIST,
                                                                                CommonClassNames.JAVA_UTIL_COLLECTION,
                                                                                CommonClassNames.JAVA_LANG_STRING);
  private final Project myProject;

  private final Map<String, GrTypeDefinition> myArrayClass = new HashMap<>();

  private final ConcurrentMap<GroovyPsiElement, PsiType> myCalculatedTypes = ContainerUtil.createConcurrentWeakMap();
  private final ConcurrentMap<PsiMember, Boolean> myCompileStatic = ContainerUtil.createConcurrentWeakMap();

  private static final RecursionGuard ourGuard = RecursionManager.createGuard("groovyPsiManager");

  public GroovyPsiManager(Project project) {
    myProject = project;

    PsiManagerEx.getInstanceEx(myProject).registerRunnableToRunOnAnyChange(() -> dropTypesCache());
  }

  public void dropTypesCache() {
    myCalculatedTypes.clear();
    myCompileStatic.clear();
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

  public PsiClassType createTypeByFQClassName(@NotNull String fqName, @NotNull GlobalSearchScope resolveScope) {
    if (ourPopularClasses.contains(fqName)) {
      PsiClass result = findClassWithCache(fqName, resolveScope);
      if (result != null) {
        return JavaPsiFacade.getElementFactory(myProject).createType(result);
      }
    }

    return JavaPsiFacade.getElementFactory(myProject).createTypeByFQClassName(fqName, resolveScope);
  }

  public boolean isCompileStatic(@NotNull PsiMember member) {
    Boolean aBoolean = myCompileStatic.get(member);
    if (aBoolean == null) {
      aBoolean = ConcurrencyUtil.cacheOrGet(myCompileStatic, member, isCompileStaticInner(member));
    }
    return aBoolean;
  }

  private boolean isCompileStaticInner(@NotNull PsiMember member) {
    PsiModifierList list = member.getModifierList();
    if (list != null) {
      PsiAnnotation compileStatic = list.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_COMPILE_STATIC);
      if (compileStatic != null) return checkForPass(compileStatic);
      PsiAnnotation typeChecked = list.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_TYPE_CHECKED);
      if (typeChecked != null) return checkForPass(typeChecked);
    }
    PsiClass aClass = member.getContainingClass();
    if (aClass != null) return isCompileStatic(aClass);
    return false;
  }

  private static boolean checkForPass(@NotNull PsiAnnotation annotation) {
    PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
    return value == null ||
           value instanceof PsiReference &&
           ResolveUtil.isEnumConstant((PsiReference)value, "PASS", GroovyCommonClassNames.GROOVY_TRANSFORM_TYPE_CHECKING_MODE);
  }

  @Nullable
  public PsiClass findClassWithCache(@NotNull String fqName, @NotNull GlobalSearchScope resolveScope) {
    return JavaPsiFacade.getInstance(myProject).findClass(fqName, resolveScope);
  }

  private static final PsiType UNKNOWN_TYPE = new GrPsiTypeStub();

  @Nullable
  public <T extends GroovyPsiElement> PsiType getType(@NotNull T element, @NotNull Function<T, PsiType> calculator) {
    PsiType type = myCalculatedTypes.get(element);
    if (type == null) {
      RecursionGuard.StackStamp stamp = ourGuard.markStack();
      type = calculator.fun(element);
      if (type == null) {
        type = UNKNOWN_TYPE;
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
      error(element, type);
    }
    return UNKNOWN_TYPE == type ? null : type;
  }

  private static void error(PsiElement element, PsiType type) {
    LOG.error("Type is invalid: " + type + "; element: " + element + " of class " + element.getClass());
  }

  @Nullable
  public GrTypeDefinition getArrayClass(@NotNull PsiType type) {
    final String typeText = type.getCanonicalText();
    GrTypeDefinition definition = myArrayClass.get(typeText);
    if (definition == null) {
      try {
        definition = GroovyPsiElementFactory.getInstance(myProject).createTypeDefinition("class __ARRAY__ { public int length; public " + typeText + "[] clone(){} }");
        myArrayClass.put(typeText, definition);
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
        return null;
      }
    }

    return definition;
  }

  @Nullable
  public static PsiType inferType(@NotNull PsiElement element, @NotNull Computable<PsiType> computable) {
    List<Object> stack = ourGuard.currentStack();
    if (stack.size() > 7) { //don't end up walking the whole project PSI
      ourGuard.prohibitResultCaching(stack.get(0));
      return null;
    }

    return ourGuard.doPreventingRecursion(element, true, computable);
  }

}
