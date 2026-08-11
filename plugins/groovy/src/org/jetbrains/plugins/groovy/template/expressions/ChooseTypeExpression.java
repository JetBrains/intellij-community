// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.template.expressions;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.PsiTypeLookupItem;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.PsiTypeResult;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.codeInsight.template.impl.JavaTemplateUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiType;
import com.intellij.psi.SmartTypePointer;
import com.intellij.psi.SmartTypePointerManager;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.completion.GroovyCompletionUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SubtypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.SupertypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.TypeConstraint;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.settings.GroovyApplicationSettings;

import java.util.ArrayList;
import java.util.List;

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
  private final GroovyApplicationSettings.Type mySelectDef;

  public ChooseTypeExpression(TypeConstraint @NotNull [] constraints, PsiManager manager, GlobalSearchScope resolveScope) {
    this(constraints, manager, resolveScope, true);
  }

  public ChooseTypeExpression(TypeConstraint[] constraints, PsiManager manager, GlobalSearchScope resolveScope, boolean addDefType) {
    this(constraints, manager, resolveScope, addDefType, GroovyApplicationSettings.Type.TYPED);
  }

  public ChooseTypeExpression(TypeConstraint[] constraints,
                              PsiManager manager,
                              GlobalSearchScope resolveScope,
                              boolean addDefType,
                              GroovyApplicationSettings.Type selectDef) {
    myAddDefType = addDefType;

    SmartTypePointerManager typePointerManager = SmartTypePointerManager.getInstance(manager.getProject());
    myTypePointer = typePointerManager.createSmartTypePointer(chooseType(constraints, resolveScope, manager));
    myItems = createItems(constraints, typePointerManager);

    mySelectDef = selectDef;
  }

  private static @NotNull List<SmartTypePointer> createItems(TypeConstraint @NotNull [] constraints, 
                                                             @NotNull SmartTypePointerManager typePointerManager) {
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

  private static void processSuperTypes(@NotNull PsiType type, @NotNull List<SmartTypePointer> result, 
                                        @NotNull SmartTypePointerManager typePointerManager) {
    result.add(typePointerManager.createSmartTypePointer(type));
    PsiType[] superTypes = type.getSuperTypes();
    for (PsiType superType : superTypes) {
      processSuperTypes(superType, result, typePointerManager);
    }
  }

  private static @NotNull PsiType chooseType(TypeConstraint @NotNull [] constraints, @NotNull GlobalSearchScope scope,
                                             @NotNull PsiManager manager) {
    return constraints.length > 0 ? constraints[0].getDefaultType() : PsiType.getJavaLangObject(manager, scope);
  }

  @Override
  public Result calculateResult(ExpressionContext context) {
    PsiFile file = context.getPsiFile();
    if (file != null) {
      PsiDocumentManager.getInstance(context.getProject()).commitDocument(file.getFileDocument());
    }
    PsiType type = myTypePointer.getType();
    if (type != null) {
      if (myAddDefType && (type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) || mySelectDef != GroovyApplicationSettings.Type.TYPED)) {
        return switch (mySelectDef) {
          case DEF, TYPED -> new TextResult(GrModifier.DEF);
          case VAR -> new TextResult(GrModifier.VAR);
          case VAL -> new TextResult(GrModifier.VAL);
          case FINAL -> new TextResult(PsiModifier.FINAL);
        };
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
      List<LookupElementBuilder> keywords = new ArrayList<>(4);
      keywords.add(LookupElementBuilder.create(GrModifier.DEF).bold());
      keywords.add(LookupElementBuilder.create(PsiModifier.FINAL).bold());
      PsiFile file = context.getPsiFile();
      if (file == null || GroovyConfigUtils.isAtLeastGroovy30(file)) {
        keywords.add(LookupElementBuilder.create(GrModifier.VAR).bold());
        if (file == null || GroovyConfigUtils.isAtLeastGroovy60(file)) {
          keywords.add(LookupElementBuilder.create(GrModifier.VAL).bold());
        }
      }
      switch (mySelectDef) {
        case DEF -> result.addFirst(keywords.removeFirst());
        case FINAL -> result.addFirst(keywords.remove(1));
        case VAR -> result.addFirst(keywords.size() > 2 ? keywords.remove(1) : keywords.removeFirst());
        case VAL -> result.addFirst(keywords.size() > 3 ? keywords.remove(2) : keywords.removeFirst());
      }
      result.addAll(keywords);
    }

    return result.toArray(LookupElement.EMPTY_ARRAY);
  }
}
