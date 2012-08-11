/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.template.expressions;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.PsiTypeResult;
import com.intellij.codeInsight.template.Result;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SubtypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author ven
 */
public class ChooseTypeExpression extends Expression {
  protected SmartTypePointer myTypePointer;
  private final LookupElement[] myItems;
  private final PsiManager myManager;

  public ChooseTypeExpression(TypeConstraint[] constraints, PsiManager manager) {
    this(constraints, manager, true);
  }

  public ChooseTypeExpression(TypeConstraint[] constraints, PsiManager manager, boolean forGroovy) {
    myManager = manager;
    myTypePointer = SmartTypePointerManager.getInstance(manager.getProject()).createSmartTypePointer(chooseType(constraints));
    myItems = createItems(constraints, forGroovy);
  }

  private static LookupElement[] createItems(TypeConstraint[] constraints, boolean forGroovy) {
    Set<LookupElement> result = new LinkedHashSet<LookupElement>();

    for (TypeConstraint constraint : constraints) {
      if (constraint instanceof SubtypeConstraint) {
        result.add(PsiTypeLookupItem.createLookupItem(constraint.getDefaultType(), null));
      } else if (constraint instanceof SupertypeConstraint) {
        processSuperTypes(constraint.getType(), result);
      }
    }

    if (forGroovy) {
      result.add(LookupElementBuilder.create(GrModifier.DEF).bold());
    }

    return result.toArray(new LookupElement[result.size()]);
  }

  private static void processSuperTypes(PsiType type, Set<LookupElement> result) {
    String text = type.getCanonicalText();
    String unboxed = PsiTypesUtil.unboxIfPossible(text);
    if (unboxed != null && !unboxed.equals(text)) {
      result.add(LookupElementBuilder.create(unboxed).bold());
    } else {
      result.add(PsiTypeLookupItem.createLookupItem(type, null));
    }
    PsiType[] superTypes = type.getSuperTypes();
    for (PsiType superType : superTypes) {
      processSuperTypes(superType, result);
    }
  }

  private PsiType chooseType(TypeConstraint[] constraints) {
    if (constraints.length > 0) return constraints[0].getDefaultType();
    return JavaPsiFacade.getInstance(myManager.getProject()).getElementFactory().createTypeByFQClassName(CommonClassNames.JAVA_LANG_OBJECT, GlobalSearchScope.allScope(myManager.getProject()));
  }

  public Result calculateResult(ExpressionContext context) {
    PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
    PsiType type = myTypePointer.getType();
    if (type != null) {
      return new PsiTypeResult(type, context.getProject()) {
        @Override
        public void handleRecalc(PsiFile psiFile, Document document, int segmentStart, int segmentEnd) {
          if (myItems.length <= 1) super.handleRecalc(psiFile, document, segmentStart, segmentEnd);
        }
      };
    }

    return null;
  }

  public Result calculateQuickResult(ExpressionContext context) {
    return calculateResult(context);
  }

  public LookupElement[] calculateLookupItems(ExpressionContext context) {
    return myItems;
  }
}
