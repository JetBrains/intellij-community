// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.template.expressions;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.JavaTemplateUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SubtypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class ChooseTypeExpression extends Expression {
  public static final InsertHandler<PsiTypeLookupItem> IMPORT_FIXER = new InsertHandler<>() {
    @Override
    public void handleInsert(@NotNull InsertionContext context, @NotNull PsiTypeLookupItem item) {
      GroovyCompletionUtil.addImportForItem(context.getFile(), context.getStartOffset(), item);
    }
  };

  protected final SmartTypePointer myTypePointer;
  private final List<SmartTypePointer> myItems;
  private final boolean myAddDefType;
  private final boolean mySelectDef;

  public ChooseTypeExpression(TypeConstraint @NotNull [] constraints, PsiManager manager, GlobalSearchScope resolveScope) {
    this(constraints, manager, resolveScope, true);
  }

  public ChooseTypeExpression(TypeConstraint[] constraints,
                              PsiManager manager,
                              GlobalSearchScope resolveScope,
                              boolean addDefType) {
    this(constraints, manager, resolveScope, addDefType, false);
  }

  public ChooseTypeExpression(TypeConstraint[] constraints,
                              PsiManager manager,
                              GlobalSearchScope resolveScope,
                              boolean addDefType,
                              boolean selectDef) {
    myAddDefType = addDefType;

    SmartTypePointerManager typePointerManager = SmartTypePointerManager.getInstance(manager.getProject());
    myTypePointer = typePointerManager.createSmartTypePointer(chooseType(constraints, resolveScope, manager));
    myItems = createItems(constraints, typePointerManager);

    mySelectDef = selectDef;
  }

  @NotNull
  private static List<SmartTypePointer> createItems(TypeConstraint @NotNull [] constraints, @NotNull SmartTypePointerManager typePointerManager) {
    List<SmartTypePointer> result = new ArrayList<>();

    for (TypeConstraint constraint : constraints) {
      if (constraint instanceof SubtypeConstraint) {
        PsiType type = constraint.getDefaultType();
        result.add(typePointerManager.createSmartTypePointer(type));
      }
      else if (constraint instanceof SupertypeConstraint) {
        processSuperTypes(constraint.getType(), result, typePointerManager);
      }
    }

    return result;
  }

  private static void processSuperTypes(@NotNull PsiType type, @NotNull List<SmartTypePointer> result, @NotNull SmartTypePointerManager typePointerManager) {
    result.add(typePointerManager.createSmartTypePointer(type));
    PsiType[] superTypes = type.getSuperTypes();
    for (PsiType superType : superTypes) {
      processSuperTypes(superType, result, typePointerManager);
    }
  }

  @NotNull
  private static PsiType chooseType(TypeConstraint @NotNull [] constraints, @NotNull GlobalSearchScope scope, @NotNull PsiManager manager) {
    if (constraints.length > 0) return constraints[0].getDefaultType();
    return PsiType.getJavaLangObject(manager, scope);
  }

  @Override
  public Result calculateResult(ExpressionContext context) {
    PsiDocumentManager.getInstance(context.getProject()).commitAllDocuments();
    PsiType type = myTypePointer.getType();
    if (type != null) {
      if (myAddDefType && (type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) || mySelectDef)) {
        return new TextResult(GrModifier.DEF);
      }

      type = TypesUtil.unboxPrimitiveTypeWrapper(type);
      if (type == null) return null;

      final PsiType finalType = type;
      return new PsiTypeResult(finalType, context.getProject()) {
        @Override
        public void handleRecalc(PsiFile psiFile, Document document, int segmentStart, int segmentEnd) {
          if (myItems.size() <= 1) {
            super.handleRecalc(psiFile, document, segmentStart, segmentEnd);
          }
          else {
            JavaTemplateUtil.updateTypeBindings(getType(), psiFile, document, segmentStart, segmentEnd, true);
          }
        }

        @Override
        public String toString() {
          return myItems.size() == 1 ? super.toString() : finalType.getPresentableText();
        }

      };
    }

    return null;
  }

  @Override
  public LookupElement[] calculateLookupItems(ExpressionContext context) {
    List<LookupElement> result = new ArrayList<>();

    for (SmartTypePointer item : myItems) {
      PsiType type = TypesUtil.unboxPrimitiveTypeWrapper(item.getType());
      if (type == null) continue;

      PsiTypeLookupItem lookupItem = PsiTypeLookupItem.createLookupItem(type, null, PsiTypeLookupItem.isDiamond(type), IMPORT_FIXER);
      result.add(lookupItem);
    }

    if (myAddDefType) {
      LookupElementBuilder def = LookupElementBuilder.create(GrModifier.DEF).bold();
      if (mySelectDef) {
        result.add(0, def);
      }
      else {
        result.add(def);
      }
    }

    return result.toArray(LookupElement.EMPTY_ARRAY);
  }
}
