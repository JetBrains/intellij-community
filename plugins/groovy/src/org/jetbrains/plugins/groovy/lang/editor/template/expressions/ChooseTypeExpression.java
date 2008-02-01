/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.editor.template.expressions;

import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.codeInsight.lookup.LookupItemUtil;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.PsiTypeResult;
import com.intellij.codeInsight.template.Result;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SubtypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeEquals;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author ven
 */
public class ChooseTypeExpression implements Expression {
  protected SmartTypePointer myTypePointer;
  private LookupItem[] myItems;
  private PsiManager myManager;

  public ChooseTypeExpression(TypeConstraint[] constraints, PsiManager manager) {
    myManager = manager;
    myTypePointer = SmartPointerManager.getInstance(manager.getProject()).createSmartTypePointer(chooseType(constraints));
    myItems = createItems(constraints);
  }

  private LookupItem[] createItems(TypeConstraint[] constraints) {
    Set<LookupItem> result = new LinkedHashSet<LookupItem>();

    for (TypeConstraint constraint : constraints) {
      if (constraint instanceof TypeEquals) {
        LookupItemUtil.addLookupItem(result, constraint.getType(), "");
      } else if (constraint instanceof SubtypeConstraint) {
        LookupItemUtil.addLookupItem(result, ((SubtypeConstraint) constraint).getDefaultType(), "");
      } else if (constraint instanceof SupertypeConstraint) {
        processSupertypes(constraint.getType(), result);
      }
    }

    LookupItem item = LookupItemUtil.objectToLookupItem("def");
    item.setBold();
    result.add(item);

    return result.toArray(new LookupItem[result.size()]);
  }

  private void processSupertypes(PsiType type, Set<LookupItem> result) {
    LookupItemUtil.addLookupItem(result, type, "");
    PsiType[] superTypes = type.getSuperTypes();
    for (PsiType superType : superTypes) {
      processSupertypes(superType, result);
    }
  }

  private PsiType chooseType(TypeConstraint[] constraints) {
    if (constraints.length > 0) return constraints[0].getType();
    return myManager.getElementFactory().createTypeByFQClassName("java.lang.Object", GlobalSearchScope.allScope(myManager.getProject()));
  }

  public Result calculateResult(ExpressionContext context) {
    PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
    PsiType type = myTypePointer.getType();
    if (type != null) {
      return new PsiTypeResult(type, PsiManager.getInstance(context.getProject()));
    }

    return null;
  }

  public Result calculateQuickResult(ExpressionContext context) {
    return calculateResult(context);
  }

  public LookupItem[] calculateLookupItems(ExpressionContext context) {
    return myItems;
  }
}
