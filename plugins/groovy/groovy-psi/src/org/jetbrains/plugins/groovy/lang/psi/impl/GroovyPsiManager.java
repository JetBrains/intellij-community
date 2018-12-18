// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

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
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import java.util.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.InferenceKt;

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
                                                                                GroovyCommonClassNames.GROOVY_OBJECT,
                                                                                GroovyCommonClassNames.GROOVY_OBJECT_SUPPORT,
                                                                                GroovyCommonClassNames.GROOVY_LANG_SCRIPT,
                                                                                CommonClassNames.JAVA_UTIL_LIST,
                                                                                CommonClassNames.JAVA_UTIL_COLLECTION,
                                                                                CommonClassNames.JAVA_LANG_STRING);
  private final Project myProject;

  private final Map<String, GrTypeDefinition> myArrayClass = new HashMap<>();

  private final ConcurrentMap<GroovyPsiElement, PsiType> myCalculatedTypes = ContainerUtil.createConcurrentWeakMap();
  private final ConcurrentMap<GrExpression, PsiType> topLevelTypes = ContainerUtil.createConcurrentWeakMap();
  private final ConcurrentMap<PsiMember, Boolean> myCompileStatic = ContainerUtil.createConcurrentWeakMap();

  private static final RecursionGuard ourGuard = RecursionManager.createGuard("groovyPsiManager");

  public GroovyPsiManager(Project project) {
    myProject = project;

    PsiManagerEx.getInstanceEx(myProject).registerRunnableToRunOnAnyChange(() -> dropTypesCache());
  }

  public void dropTypesCache() {
    myCalculatedTypes.clear();
    topLevelTypes.clear();
    myCompileStatic.clear();
  }

  public static GroovyPsiManager getInstance(Project project) {
    return ServiceManager.getService(project, GroovyPsiManager.class);
  }

  public PsiClassType createTypeByFQClassName(@NotNull String fqName, @NotNull GlobalSearchScope resolveScope) {
    if (ourPopularClasses.contains(fqName)) {
      PsiClass result = JavaPsiFacade.getInstance(myProject).findClass(fqName, resolveScope);
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
    PsiAnnotation annotation = getCompileStaticAnnotation(member);
    if (annotation != null) return checkForPass(annotation);
    PsiClass aClass = member.getContainingClass();
    if (aClass != null) return isCompileStatic(aClass);
    return false;
  }

  @Nullable
  public static PsiAnnotation getCompileStaticAnnotation(@NotNull PsiMember member) {
    PsiModifierList list = member.getModifierList();
    if (list == null) return null;
    PsiAnnotation compileStatic = list.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_COMPILE_STATIC);
    if (compileStatic != null) return compileStatic;
    return list.findAnnotation(GroovyCommonClassNames.GROOVY_TRANSFORM_TYPE_CHECKED);
  }

  public static boolean checkForPass(@NotNull PsiAnnotation annotation) {
    PsiAnnotationMemberValue value = annotation.findAttributeValue("value");
    return value == null ||
           value instanceof PsiReference &&
           ResolveUtil.isEnumConstant((PsiReference)value, "PASS", GroovyCommonClassNames.GROOVY_TRANSFORM_TYPE_CHECKING_MODE);
  }

  @Nullable
  @Deprecated
  public PsiClass findClassWithCache(@NotNull String fqName, @NotNull GlobalSearchScope resolveScope) {
    return JavaPsiFacade.getInstance(myProject).findClass(fqName, resolveScope);
  }

  private static final PsiType UNKNOWN_TYPE = new GrPsiTypeStub();

  @Nullable
  public <T extends GroovyPsiElement> PsiType getType(@NotNull T element, @NotNull Function<? super T, ? extends PsiType> calculator) {
    return getTypeWithCaching(element, myCalculatedTypes, calculator);
  }

  @Nullable
  public PsiType getTopLevelType(@NotNull GrExpression expression) {
    return getTypeWithCaching(expression, topLevelTypes, InferenceKt::getTopLevelType);
  }

  @Nullable
  private static <K extends GroovyPsiElement> PsiType getTypeWithCaching(@NotNull K key, @NotNull ConcurrentMap<? super K, PsiType> map, @NotNull Function<? super K, ? extends PsiType> calculator) {
    PsiType type = map.get(key);
    if (type == null) {
      RecursionGuard.StackStamp stamp = ourGuard.markStack();
      type = calculator.fun(key);
      if (type == null) {
        type = UNKNOWN_TYPE;
      }
      if (stamp.mayCacheNow()) {
        type = ConcurrencyUtil.cacheOrGet(map, key, type);
      } else {
        final PsiType alreadyInferred = map.get(key);
        if (alreadyInferred != null) {
          type = alreadyInferred;
        }
      }
    }
    if (!type.isValid()) {
      error(key, type);
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
  public static PsiType inferType(@NotNull PsiElement element, @NotNull Computable<? extends PsiType> computable) {
    List<Object> stack = ourGuard.currentStack();
    if (stack.size() > 7) { //don't end up walking the whole project PSI
      ourGuard.prohibitResultCaching(stack.get(0));
      return null;
    }

    return ourGuard.doPreventingRecursion(element, true, computable);
  }

}
