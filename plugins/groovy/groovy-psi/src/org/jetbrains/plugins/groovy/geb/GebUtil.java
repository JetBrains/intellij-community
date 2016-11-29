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
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightField;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.util.LightCacheKey;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class GebUtil {

  private static final LightCacheKey<Map<String, PsiField>> KEY = LightCacheKey.createByFileModificationCount();

  public static boolean contributeMembersInsideTest(PsiScopeProcessor processor,
                                                    PsiElement place,
                                                    ResolveState state) {
    GroovyPsiManager groovyPsiManager = GroovyPsiManager.getInstance(place.getProject());

    PsiClass browserClass = groovyPsiManager.findClassWithCache("geb.Browser", place.getResolveScope());
    if (browserClass != null) {
      if (!browserClass.processDeclarations(processor, state, null, place)) return false;

      PsiClass pageClass = groovyPsiManager.findClassWithCache("geb.Page", place.getResolveScope());

      if (pageClass != null) {
        if (!pageClass.processDeclarations(processor, state, null, place)) return false;
      }
    }

    return true;
  }

  public static Map<String, PsiField> getContentElements(@NotNull PsiClass pageOrModuleClass) {
    Map<String, PsiField> res = KEY.getCachedValue(pageOrModuleClass);
    if (res == null) {
      res = calculateContentElements(pageOrModuleClass);
      res = KEY.putCachedValue(pageOrModuleClass, res);
    }

    return res;
  }

  private static Map<String, PsiField> calculateContentElements(@NotNull PsiClass pageOrModuleClass) {
    PsiField contentField = pageOrModuleClass.findFieldByName("content", false);

    if (!(contentField instanceof GrField)) return Collections.emptyMap();

    GrExpression initializer = ((GrField)contentField).getInitializerGroovy();
    if (!(initializer instanceof GrClosableBlock)) return Collections.emptyMap();

    Map<String, PsiField> res = new HashMap<>();
    PsiType objectType = PsiType.getJavaLangObject(pageOrModuleClass.getManager(), pageOrModuleClass.getResolveScope());

    for (PsiElement e = initializer.getFirstChild(); e != null; e = e.getNextSibling()) {
      if (e instanceof GrMethodCall) {
        GrMethodCall methodCall = (GrMethodCall)e;

        GrExpression invokedExpression = methodCall.getInvokedExpression();
        if (!(invokedExpression instanceof GrReferenceExpression)) continue;
        if (((GrReferenceExpression)invokedExpression).isQualified()) continue;

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

        GrLightField field = new GrLightField(pageOrModuleClass, ((GrReferenceExpression)invokedExpression).getReferenceName(), objectType, invokedExpression) {
          @Override
          public PsiType getTypeGroovy() {
            return block.getReturnType();
          }

          @Override
          public PsiType getDeclaredType() {
            return null;
          }
        };

        field.getModifierList().addModifier(GrModifierFlags.STATIC_MASK);

        res.put(field.getName(), field);
      }
    }

    return res;
  }
}
