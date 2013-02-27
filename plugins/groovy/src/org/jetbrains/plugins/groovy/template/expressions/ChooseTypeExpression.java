/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SubtypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author ven
 */
public class ChooseTypeExpression extends Expression {
  public static final InsertHandler<PsiTypeLookupItem> IMPORT_FIXER = new InsertHandler<PsiTypeLookupItem>() {
    @Override
    public void handleInsert(InsertionContext context, PsiTypeLookupItem item) {
      GroovyCompletionUtil.addImportForItem(context.getFile(), context.getStartOffset(), item);
    }
  };
  protected final SmartTypePointer myTypePointer;
  private final LookupElement[] myItems;
  private final PsiManager myManager;
  private final boolean myForGroovy;

  public ChooseTypeExpression(TypeConstraint[] constraints, PsiManager manager, GlobalSearchScope resolveScope) {
    this(constraints, manager, true, resolveScope);
  }

  public ChooseTypeExpression(TypeConstraint[] constraints, PsiManager manager, boolean forGroovy, GlobalSearchScope resolveScope) {
    myManager = manager;
    myForGroovy = forGroovy;
    myTypePointer = SmartTypePointerManager.getInstance(manager.getProject()).createSmartTypePointer(chooseType(constraints, resolveScope));
    myItems = createItems(constraints, forGroovy);
  }

  private static LookupElement[] createItems(TypeConstraint[] constraints, boolean forGroovy) {
    Set<LookupElement> result = new LinkedHashSet<LookupElement>();

    if (forGroovy && constraints.length == 1 && constraints[0].getDefaultType().equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
      result.add(LookupElementBuilder.create(GrModifier.DEF).bold());
    }
    for (TypeConstraint constraint : constraints) {
      if (constraint instanceof SubtypeConstraint) {
        PsiType type = constraint.getDefaultType();
        PsiTypeLookupItem item = PsiTypeLookupItem.createLookupItem(type, null, PsiTypeLookupItem.isDiamond(type), IMPORT_FIXER);
        result.add(item);
      }
      else if (constraint instanceof SupertypeConstraint) {
        processSuperTypes(constraint.getType(), result);
      }
    }

    if (forGroovy) { //don't check whether we already added 'def' 'cause it is a set
      result.add(LookupElementBuilder.create(GrModifier.DEF).bold());
    }

    return result.toArray(new LookupElement[result.size()]);
  }

  private static void processSuperTypes(PsiType type, Set<LookupElement> result) {
    String text = type.getCanonicalText();
    String unboxed = PsiTypesUtil.unboxIfPossible(text);
    if (unboxed != null && !unboxed.equals(text)) {
      result.add(LookupElementBuilder.create(unboxed).bold());
    }
    else {
      PsiTypeLookupItem item = PsiTypeLookupItem.createLookupItem(type, null, PsiTypeLookupItem.isDiamond(type), IMPORT_FIXER);
      result.add(item);
    }
    PsiType[] superTypes = type.getSuperTypes();
    for (PsiType superType : superTypes) {
      processSuperTypes(superType, result);
    }
  }

  private PsiType chooseType(TypeConstraint[] constraints, GlobalSearchScope scope) {
    if (constraints.length > 0) return constraints[0].getDefaultType();
    return PsiType.getJavaLangObject(myManager, scope);
  }

  public Result calculateResult(ExpressionContext context) {
    PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
    PsiType type = myTypePointer.getType();
    if (type != null) {
      if (myForGroovy && type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
        return new TextResult(GrModifier.DEF);
      }

      type = TypesUtil.unboxPrimitiveTypeWrapper(type);
      if (type == null) return null;

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
