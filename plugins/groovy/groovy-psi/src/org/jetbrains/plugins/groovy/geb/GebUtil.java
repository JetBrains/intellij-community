// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.geb;

import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierFlags;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightField;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.util.LightCacheKey;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class GebUtil {

  private static final LightCacheKey<Map<String, PsiMember>> KEY = LightCacheKey.createByFileModificationCount();

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
      }
    }

    return true;
  }

  public static Map<String, PsiMember> getContentElements(@NotNull PsiClass pageOrModuleClass) {
    Map<String, PsiMember> res = KEY.getCachedValue(pageOrModuleClass);
    if (res == null) {
      res = calculateContentElements(pageOrModuleClass);
      res = KEY.putCachedValue(pageOrModuleClass, res);
    }

    return res;
  }

  private static Map<String, PsiMember> calculateContentElements(@NotNull PsiClass pageOrModuleClass) {
    PsiField contentField = pageOrModuleClass.findFieldByName("content", false);

    if (!(contentField instanceof GrField)) return Collections.emptyMap();

    GrExpression initializer = ((GrField)contentField).getInitializerGroovy();
    if (!(initializer instanceof GrClosableBlock)) return Collections.emptyMap();

    Map<String, PsiMember> res = new HashMap<>();
    PsiType objectType = PsiType.getJavaLangObject(pageOrModuleClass.getManager(), pageOrModuleClass.getResolveScope());

    for (PsiElement e = initializer.getFirstChild(); e != null; e = e.getNextSibling()) {
      if (e instanceof GrMethodCall) {
        GrMethodCall methodCall = (GrMethodCall)e;

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

        if (type == null) {
          return super.myType;
        }

        return type;
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
