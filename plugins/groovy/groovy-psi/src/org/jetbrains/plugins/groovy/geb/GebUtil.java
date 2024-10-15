// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.geb;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.CachedValueProvider.Result;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrLabeledStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.ClassUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.GrReferenceExpressionImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightField;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class GebUtil {

  public static boolean contributeMembersInsideTest(PsiScopeProcessor processor,
                                                    PsiElement place,
                                                    ResolveState state) {
    JavaPsiFacade facade = JavaPsiFacade.getInstance(place.getProject());

    PsiClass browserClass = facade.findClass("geb.Browser", place.getResolveScope());
    if (browserClass != null) {
      if (!browserClass.processDeclarations(processor, state, null, place)) return false;

      PsiClass pageClass = facade.findClass("geb.Page", place.getResolveScope());

      if (pageClass != null) {
        if (!pageClass.processDeclarations(processor, state, null, place)) return false;
        contributePageContent(processor, state, place);
      }
    }

    return true;
  }

  private static void contributePageContent(PsiScopeProcessor processor, ResolveState state, PsiElement place) {
    if(place instanceof GrReferenceExpressionImpl expr
       && !(expr.getParent() instanceof GrMethodCall)) {

      PsiClassType gebPageType = PsiType.getTypeByName("geb.Page", place.getProject(), place.getResolveScope());
      PsiClass ourPage = findPageChange(expr, gebPageType);
      if (ourPage != null) {
        Map<String, PsiClass> supers = ClassUtil.getSuperClassesWithCache(ourPage);
        String nameHint = ResolveUtil.getNameHint(processor);

        for (PsiClass psiClass : supers.values()) {
          Map<String, PsiMember> contentElements = getContentElements(psiClass);

          if (nameHint == null) {
            for (Map.Entry<String, PsiMember> entry : contentElements.entrySet()) {
              processor.execute(entry.getValue(), state);
            }
            return;
          }
          else {
            PsiVariable defElement = (PsiVariable)contentElements.get(nameHint);
            if (defElement != null) {
              processor.execute(defElement, state);
            }
          }
        }
      }
    }
  }

  
  private static @Nullable PsiClass findPageChange(PsiElement place, PsiClassType pageType) {
    PsiElement currentElement = place;
    PsiClass ourPage = null;
    while (currentElement != null) {
      PsiElement examineThis = currentElement;
      if (currentElement instanceof GrLabeledStatement labeled) {
        examineThis = labeled.getStatement();
      }
      if(examineThis instanceof GrVariableDeclaration) {
        PsiElement findCall = examineThis.getLastChild();
        while(findCall != null) {
          if(findCall instanceof GrExpression) {
            examineThis = findCall;
            break;
          }
          findCall = findCall.getLastChild();
        }
      }
      if (examineThis instanceof GrExpression call && !(examineThis instanceof GrReferenceExpression)) {
        PsiType ret = call.getType();
        if (ret != null && pageType.isAssignableFrom(ret) && ret instanceof PsiImmediateClassType ct) {
          ourPage = ct.resolve();
          break;
        }
      }
      currentElement = currentElement.getPrevSibling();
    }

    if (ourPage == null) {
      PsiElement parent = place.getParent();
      if (parent != null && !(parent instanceof GrMethod))
        return findPageChange(parent, pageType);
    }

    return ourPage;
  }

  public static Map<String, PsiMember> getContentElements(@NotNull PsiClass pageOrModuleClass) {
    return CachedValuesManager.getCachedValue(pageOrModuleClass, () -> Result.create(
      calculateContentElements(pageOrModuleClass), pageOrModuleClass
    ));
  }

  private static Map<String, PsiMember> calculateContentElements(@NotNull PsiClass pageOrModuleClass) {
    PsiField contentField = pageOrModuleClass.findFieldByName("content", false);

    if (!(contentField instanceof GrField)) return Collections.emptyMap();

    GrExpression initializer = ((GrField)contentField).getInitializerGroovy();
    if (!(initializer instanceof GrClosableBlock)) return Collections.emptyMap();

    Map<String, PsiMember> res = new HashMap<>();
    PsiType objectType = PsiType.getJavaLangObject(pageOrModuleClass.getManager(), pageOrModuleClass.getResolveScope());

    for (PsiElement e = initializer.getFirstChild(); e != null; e = e.getNextSibling()) {
      if (e instanceof GrMethodCall methodCall) {

        GrExpression invokedExpression = methodCall.getInvokedExpression();
        if (!(invokedExpression instanceof GrReferenceExpression)) continue;
        if (((GrReferenceExpression)invokedExpression).isQualified()) continue;
        String name = ((GrReferenceExpression)invokedExpression).getReferenceName();
        if (name == null) continue;

        GrExpression[] arguments = PsiUtil.getAllArguments((GrCall)e);
        if (arguments.length == 0) continue;

        final GrClosableBlock block;
        if (arguments.length == 1 && arguments[0] instanceof GrClosableBlock) {
          block = (GrClosableBlock)arguments[0];
        }
        else if (arguments.length == 2 && arguments[0] == null && arguments[1] instanceof GrClosableBlock) {
          block = (GrClosableBlock)arguments[1];
        }
        else {
          continue;
        }

        PsiMember target;
        if (block.hasParametersSection()) {
          target = extractMethodForContent(pageOrModuleClass, name, invokedExpression, block);
        }
        else {
          target = extractFieldForContent(pageOrModuleClass, objectType, name, invokedExpression, block);
        }
        res.put(name, target);
      }
    }

    return res;
  }

  @NotNull
  private static PsiField extractFieldForContent(@NotNull PsiClass pageOrModuleClass,
                                                 @NotNull PsiType objectType, String name,
                                                 @NotNull GrExpression invokedExpression,
                                                 @NotNull GrClosableBlock block) {
    GrLightField field = new GrLightField(pageOrModuleClass, name, objectType, invokedExpression) {

      @Override
      @NotNull
      public PsiType getType() {
        PsiType type = block.getReturnType();
        return type != null ? type : super.getType();
      }

      @Override
      public PsiType getDeclaredType() {
        return null;
      }
    };
    field.getModifierList().addModifier(GrModifierFlags.STATIC_MASK);
    return field;
  }

  @NotNull
  private static PsiMethod extractMethodForContent(@NotNull PsiClass pageOrModuleClass,
                                                   @NotNull String name,
                                                   @NotNull GrExpression invokedExpression,
                                                   @NotNull GrClosableBlock block) {
    GrLightMethodBuilder method = new GrLightMethodBuilder(pageOrModuleClass.getManager(), name) {
      @Override
      public PsiType getReturnType() {
        return block.getReturnType();
      }
    };
    method.setContainingClass(pageOrModuleClass);
    method.addModifier(GrModifierFlags.STATIC_MASK);
    method.setNavigationElement(invokedExpression);
    for (GrParameter parameter : block.getAllParameters()) {
      method.addParameter(parameter);
    }
    return method;
  }
}
