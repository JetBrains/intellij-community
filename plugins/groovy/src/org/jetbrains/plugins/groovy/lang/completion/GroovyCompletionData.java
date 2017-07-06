/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.completion;


import com.intellij.codeInsight.TailType;
import com.intellij.codeInsight.TailTypes;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.JavaKeywordCompletion;
import com.intellij.codeInsight.completion.ModifierChooser;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.TailTypeDecorator;
import com.intellij.lang.ASTNode;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.*;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.groovydoc.lexer.GroovyDocTokenTypes;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocInlinedTag;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.formatter.GrControlStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrTraditionalForClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author ilyas
 */
public class GroovyCompletionData {
  public static final String[] BUILT_IN_TYPES = {"boolean", "byte", "char", "short", "int", "float", "long", "double", "void"};
  public static final String[] MODIFIERS = new String[]{"private", "public", "protected", "transient", "abstract", "native", "volatile", "strictfp", "static"};
  public static final ElementPattern<PsiElement> IN_CAST_TYPE_ELEMENT = StandardPatterns.or(
    PsiJavaPatterns.psiElement().afterLeaf(PsiJavaPatterns.psiElement().withText("(").withParent(
      PsiJavaPatterns.psiElement(GrParenthesizedExpression.class, GrTypeCastExpression.class))),
    PsiJavaPatterns
      .psiElement().afterLeaf(PsiJavaPatterns.psiElement().withElementType(GroovyTokenTypes.kAS).withParent(GrSafeCastExpression.class))
  );
  static final String[] INLINED_DOC_TAGS = {"code", "docRoot", "inheritDoc", "link", "linkplain", "literal"};
  static final String[] DOC_TAGS = {"author", "deprecated", "exception", "param", "return", "see", "serial", "serialData",
      "serialField", "since", "throws", "version"};

  public static void addGroovyKeywords(CompletionParameters parameters, CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    PsiElement parent = position.getParent();
    if (parent instanceof GrLiteral) {
      return;
    }

    final String[] extendsImplements = addExtendsImplements(position);
    for (String keyword : extendsImplements) {
      result.addElement(keyword(keyword, TailType.HUMBLE_SPACE_BEFORE_WORD));
    }
    if (extendsImplements.length > 0) {
      return;
    }


    if (parent instanceof GrExpression && parent.getParent() instanceof GrAnnotationNameValuePair) {
      addKeywords(result, false, PsiKeyword.TRUE, PsiKeyword.FALSE, PsiKeyword.NULL);
      return;
    }

    if (afterAtInType(position)) {
      result.addElement(keyword(PsiKeyword.INTERFACE, TailType.HUMBLE_SPACE_BEFORE_WORD));
    }

    if (!PlatformPatterns.psiElement().afterLeaf(".", ".&", "@", "*.", "?.").accepts(position)) {
      if (afterAbstractMethod(position, false, true)) {
        result.addElement(keyword(PsiKeyword.THROWS, TailType.HUMBLE_SPACE_BEFORE_WORD));
        if (afterAbstractMethod(position, false, false)) return;
      }

      if (suggestPackage(position)) {
        result.addElement(keyword(PsiKeyword.PACKAGE, TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
      if (suggestImport(position)) {
        result.addElement(keyword(PsiKeyword.IMPORT, TailType.HUMBLE_SPACE_BEFORE_WORD));
      }

      addTypeDefinitionKeywords(result, position);

      if (isAfterAnnotationMethodIdentifier(position)) {
        result.addElement(keyword(PsiKeyword.DEFAULT, TailType.HUMBLE_SPACE_BEFORE_WORD));
      }

      addExtendsForTypeParams(position, result);

      registerControlCompletion(position, result);

      if (parent instanceof GrExpression || isInfixOperatorPosition(position)) {
        addKeywords(result, false, PsiKeyword.TRUE, PsiKeyword.FALSE, PsiKeyword.NULL, PsiKeyword.SUPER, PsiKeyword.THIS);
        result.addElement(keyword(PsiKeyword.NEW, TailType.HUMBLE_SPACE_BEFORE_WORD));
      }

      if (isAfterForParameter(position)) {
        result.addElement(keyword("in", TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
      if (isInfixOperatorPosition(position)) {
        addKeywords(result, true, "as", "in", PsiKeyword.INSTANCEOF);
      }
      if (suggestPrimitiveTypes(position)) {
        final boolean addSpace = !IN_CAST_TYPE_ELEMENT.accepts(position) && !GroovySmartCompletionContributor.AFTER_NEW.accepts(position) && !isInExpression(position);
        addKeywords(result, addSpace, BUILT_IN_TYPES);
      }

      if (PsiJavaPatterns.psiElement(GrReferenceExpression.class).inside(
        StandardPatterns.or(PsiJavaPatterns.psiElement(GrWhileStatement.class), PsiJavaPatterns.psiElement(GrForStatement.class))).accepts(parent)) {
        addKeywords(result, false, PsiKeyword.BREAK, PsiKeyword.CONTINUE);
      }
      else if (PsiJavaPatterns.psiElement(GrReferenceExpression.class).inside(GrCaseSection.class).accepts(parent)) {
        addKeywords(result, false, PsiKeyword.BREAK);
      }

      if (PsiJavaPatterns.psiElement().withSuperParent(2, GrImportStatement.class).accepts(position)) {
        if (PsiJavaPatterns.psiElement().afterLeaf(PsiKeyword.IMPORT).accepts(position)) {
          addKeywords(result, true, PsiKeyword.STATIC);
        }
      } else {
        if (suggestModifiers(position)) {
          addModifiers(position, result);
        }
        if (PsiJavaPatterns.psiElement().afterLeaf(MODIFIERS).accepts(position) ||
            GroovyCompletionUtil.isInTypeDefinitionBody(position) && GroovyCompletionUtil.isNewStatement(position, true)) {
          addKeywords(result, true, PsiKeyword.SYNCHRONIZED);
        }
        if (suggestFinalDef(position) || PsiJavaPatterns
          .psiElement().afterLeaf(PsiJavaPatterns.psiElement().withText("(").withParent(GrForStatement.class)).accepts(position)) {
          addKeywords(result, true, PsiKeyword.FINAL, "def");
        }
      }
    }
  }

  private static boolean isAfterAnnotationMethodIdentifier(@NotNull PsiElement position) {
    final PsiElement parent = position.getParent();

    if (parent instanceof GrTypeDefinitionBody) {
      final GrTypeDefinition containingClass = (GrTypeDefinition)parent.getParent();
      if (containingClass.isAnnotationType()) {
        PsiElement sibling = PsiUtil.skipWhitespacesAndComments(position.getPrevSibling(), false);
        if (sibling instanceof PsiErrorElement) {
          sibling = PsiUtil.skipWhitespacesAndComments(sibling.getPrevSibling(), false);
        }
        return sibling instanceof GrAnnotationMethod && ((GrAnnotationMethod)sibling).getDefaultValue() == null;
      }
    }
    return false;
  }

  /**
   * checks whether promitive type used in expression
   */
  private static boolean isInExpression(PsiElement position) {
    final PsiElement actual = position.getParent();
    final PsiElement parent = actual.getParent();
    return parent instanceof GrArgumentList || parent instanceof GrBinaryExpression;
  }

  private static void addExtendsForTypeParams(PsiElement position, CompletionResultSet result) {
    if (GroovyCompletionUtil.isWildcardCompletion(position)) {
      addKeywords(result, true, PsiKeyword.EXTENDS, PsiKeyword.SUPER);
    }
  }

  private static boolean isAfterForParameter(PsiElement position) {
    ElementPattern<PsiElement> forParameter =
      PsiJavaPatterns.psiElement().withParents(GrParameter.class, GrTraditionalForClause.class, GrForStatement.class);
    return PsiJavaPatterns.psiElement().withParent(GrReferenceExpression.class).afterLeaf(forParameter).accepts(position) ||
           forParameter.accepts(position) && PsiJavaPatterns.psiElement().afterLeaf(PsiJavaPatterns.psiElement(GroovyTokenTypes.mIDENT)).accepts(position);
  }

  public static void addModifiers(PsiElement position, CompletionResultSet result) {
    PsiClass scope = PsiTreeUtil.getParentOfType(position, PsiClass.class);
    PsiModifierList modifierList = ModifierChooser.findModifierList(position);
    addKeywords(result, true, ModifierChooser.addMemberModifiers(modifierList, scope != null && scope.isInterface(), position));
  }

  private static void addTypeDefinitionKeywords(CompletionResultSet result, PsiElement position) {
    if (suggestClassInterfaceEnum(position)) {
      addKeywords(result, true, PsiKeyword.CLASS, PsiKeyword.INTERFACE, PsiKeyword.ENUM, GroovyTokenTypes.kTRAIT.toString());
    }
  }

  @NotNull
  private static String[] addExtendsImplements(PsiElement context) {
    if (context.getParent() == null) {
      return ArrayUtil.EMPTY_STRING_ARRAY;
    }

    PsiElement elem = context.getParent();
    boolean ext = !(elem instanceof GrExtendsClause);
    boolean impl = !(elem instanceof GrImplementsClause);

    if (elem instanceof GrTypeDefinitionBody) { //inner class
      elem = PsiUtil.skipWhitespacesAndComments(context.getPrevSibling(), false);
    }
    else {
      if (elem instanceof GrReferenceExpression && PsiUtil.skipWhitespacesAndComments(elem.getPrevSibling(), false) instanceof GrTypeDefinition) {
        elem = PsiUtil.skipWhitespacesAndComments(elem.getPrevSibling(), false);
      }
      else {
        PsiElement parent = elem.getParent();
        if (parent != null) {
          if (parent instanceof PsiFile) {
            elem = null;
          }
          else {
            elem = PsiUtil.skipWhitespacesAndComments(parent.getPrevSibling(), false);
          }
        }
      }
    }

    ext &= elem instanceof GrInterfaceDefinition || elem instanceof GrClassDefinition || elem instanceof GrTraitTypeDefinition;
    impl &= elem instanceof GrEnumTypeDefinition || elem instanceof GrClassDefinition || elem instanceof GrTraitTypeDefinition;
    if (!ext && !impl) return ArrayUtil.EMPTY_STRING_ARRAY;

    PsiElement[] children = elem.getChildren();
    for (PsiElement child : children) {
      ext &= !(child instanceof GrExtendsClause && ((GrExtendsClause)child).getKeyword() != null);
      if (child instanceof GrImplementsClause && ((GrImplementsClause)child).getKeyword() != null || child instanceof GrTypeDefinitionBody) {
        return ArrayUtil.EMPTY_STRING_ARRAY;
      }
    }
    if (ext && impl) {
      return new String[]{PsiKeyword.EXTENDS, PsiKeyword.IMPLEMENTS};
    }

    return new String[]{ext ? PsiKeyword.EXTENDS : PsiKeyword.IMPLEMENTS};
  }

  public static void addKeywords(CompletionResultSet result, boolean space, String... keywords) {
    for (String s : keywords) {
      result.addElement(keyword(s, space ? TailType.HUMBLE_SPACE_BEFORE_WORD : TailType.NONE));
    }
  }

  private static LookupElement keyword(final String keyword, @NotNull TailType tail) {
    LookupElementBuilder element = LookupElementBuilder.create(keyword).bold();
    return tail != TailType.NONE ? new JavaKeywordCompletion.OverridableSpace(element, tail) : element;
  }

  private static void registerControlCompletion(PsiElement context, CompletionResultSet result) {
    if (isControlStructure(context)) {
      result.addElement(keyword(PsiKeyword.TRY, TailTypes.TRY_LBRACE));
      result.addElement(keyword(PsiKeyword.WHILE, TailTypes.WHILE_LPARENTH));
      result.addElement(keyword(PsiKeyword.SWITCH, TailTypes.SWITCH_LPARENTH));
      result.addElement(keyword(PsiKeyword.FOR, TailTypes.FOR_LPARENTH));
      result.addElement(keyword(PsiKeyword.THROW, TailType.HUMBLE_SPACE_BEFORE_WORD));
      result.addElement(keyword(PsiKeyword.ASSERT, TailType.HUMBLE_SPACE_BEFORE_WORD));
      result.addElement(keyword(PsiKeyword.SYNCHRONIZED, TailTypes.SYNCHRONIZED_LPARENTH));
      result.addElement(keyword(PsiKeyword.RETURN, hasReturnValue(context) ? TailType.HUMBLE_SPACE_BEFORE_WORD : TailType.NONE));
    }
    if (inCaseSection(context)) {
      result.addElement(keyword("case", TailType.HUMBLE_SPACE_BEFORE_WORD));
      result.addElement(keyword("default", TailType.CASE_COLON));
    }
    if (afterTry(context)) {
      result.addElement(keyword(PsiKeyword.CATCH, TailTypes.CATCH_LPARENTH));
      result.addElement(keyword(PsiKeyword.FINALLY, TailTypes.FINALLY_LBRACE));
    }
    if (afterIfOrElse(context)) {
      result.addElement(keyword(PsiKeyword.ELSE, TailType.HUMBLE_SPACE_BEFORE_WORD));
    }

    if (isCommandCallWithOneArg(context)) {
      result.addElement(keyword(PsiKeyword.ASSERT, TailType.HUMBLE_SPACE_BEFORE_WORD));
      if (hasReturnValue(context)) {
        result.addElement(keyword(PsiKeyword.RETURN, TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
    }
  }

  private static boolean isCommandCallWithOneArg(PsiElement context) {
    return context.getParent() instanceof GrReferenceExpression &&
           context.getParent().getParent() instanceof GrApplicationStatement &&
           ((GrApplicationStatement)context.getParent().getParent()).getExpressionArguments().length == 1 &&
           !PsiImplUtil.hasNamedArguments(((GrApplicationStatement)context.getParent().getParent()).getArgumentList());
  }

  private static boolean hasReturnValue(PsiElement context) {
    GrControlFlowOwner flowOwner = ControlFlowUtils.findControlFlowOwner(context);
    if (flowOwner instanceof GrClosableBlock) return true;
    if (flowOwner instanceof GroovyFile) return true;
    if (flowOwner == null) return true;

    PsiElement parent = flowOwner.getParent();
    if (parent instanceof GrMethod) {
      return !PsiType.VOID.equals(((GrMethod)parent).getReturnType());
    }
    else if (parent instanceof GrClassInitializer) {
      return false;
    }

    return true;
  }

  public static void addGroovyDocKeywords(CompletionParameters parameters, CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    if (PlatformPatterns.psiElement(GroovyDocTokenTypes.mGDOC_TAG_NAME).andNot(PlatformPatterns.psiElement().afterLeaf(".")).accepts(
      position)) {
      String[] tags = position.getParent() instanceof GrDocInlinedTag ? INLINED_DOC_TAGS : DOC_TAGS;
      for (String docTag : tags) {
        result.addElement(TailTypeDecorator.withTail(LookupElementBuilder.create(docTag), TailType.HUMBLE_SPACE_BEFORE_WORD));
      }
    }
  }

  private static boolean suggestPackage(PsiElement context) {
    if (context.getParent() != null &&
        !(context.getParent() instanceof PsiErrorElement) &&
        context.getParent().getParent() instanceof GroovyFile &&
        ((GroovyFile) context.getParent().getParent()).getPackageDefinition() == null) {
      if (context.getParent() instanceof GrReferenceExpression) {
        return true;
      }
      if (context.getParent() instanceof GrApplicationStatement &&
          ((GrApplicationStatement) context.getParent()).getExpressionArguments()[0] instanceof GrReferenceExpression) {
        return true;
      }
      return false;
    }
    if (context.getTextRange().getStartOffset() == 0 && !(context instanceof OuterLanguageElement)) {
      return true;
    }

    final PsiElement leaf = GroovyCompletionUtil.getLeafByOffset(context.getTextRange().getStartOffset() - 1, context);
    if (leaf != null) {
      PsiElement parent = leaf.getParent();
      if (parent instanceof GroovyFile) {
        GroovyFile groovyFile = (GroovyFile) parent;
        if (groovyFile.getPackageDefinition() == null) {
          return GroovyCompletionUtil.isNewStatement(context, false);
        }
      }
    }

    return false;
  }

  private static boolean suggestImport(PsiElement context) {
    if (context.getParent() != null &&
        !(context.getParent() instanceof PsiErrorElement) &&
        GroovyCompletionUtil.isNewStatement(context, false) &&
        context.getParent().getParent() instanceof GroovyFile) {
      return true;
    }
    final PsiElement leaf = GroovyCompletionUtil.getLeafByOffset(context.getTextRange().getStartOffset() - 1, context);
    if (leaf != null) {
      PsiElement parent = leaf.getParent();
      if (parent instanceof GroovyFile) {
        return GroovyCompletionUtil.isNewStatement(context, false);
      }
    }
    return context.getTextRange().getStartOffset() == 0 && !(context instanceof OuterLanguageElement);
  }

  public static boolean suggestClassInterfaceEnum(PsiElement context) {
    PsiElement nextNonSpace = PsiUtil.getNextNonSpace(context);
    if (nextNonSpace instanceof PsiErrorElement) nextNonSpace = PsiUtil.getNextNonSpace(nextNonSpace);
    if (afterAbstractMethod(context, true, false) && nextNonSpace != null && nextNonSpace.getText().startsWith("{") || addExtendsImplements(context).length > 0) {
      return false;
    }

    PsiElement parent = context.getParent();
    if (parent instanceof GrTypeDefinitionBody) {
      return true;
    }

    if (parent instanceof GrReferenceExpression) {
      if (parent.getParent() instanceof GroovyFile) {
        return true;
      }
      if ((parent.getParent() instanceof GrApplicationStatement ||
           parent.getParent() instanceof GrCall) &&
          parent.getParent().getParent() instanceof GroovyFile) {
        return true;
      }
    }

    /*
    @Anno
    cl<caret>
     */
    if (parent instanceof GrVariable && context == ((GrVariable)parent).getNameIdentifierGroovy()) {
      final PsiElement decl = parent.getParent();
      if (decl instanceof GrVariableDeclaration &&
          !((GrVariableDeclaration)decl).isTuple() &&
          ((GrVariableDeclaration)decl).getTypeElementGroovy() == null &&
          (decl.getParent() instanceof GrTypeDefinitionBody || decl.getParent() instanceof GroovyFile)) {
        return true;
      }
    }

    final PsiElement leaf = GroovyCompletionUtil.getLeafByOffset(context.getTextRange().getStartOffset() - 1, context);
    if (leaf != null) {
      PsiElement prev = leaf;
      prev = PsiImplUtil.realPrevious(prev);
      if (prev instanceof GrModifierList &&
          prev.getParent() != null &&
          prev.getParent().getParent() instanceof GroovyFile) {
        return true;
      }

      if (leaf.getParent() instanceof GroovyFile) {
        return GroovyCompletionUtil.isNewStatement(context, false);
      }
    }

    return false;
  }

  private static boolean afterAtInType(PsiElement context) {
    PsiElement previous = PsiImplUtil.realPrevious(PsiTreeUtil.prevLeaf(context));
    if (previous != null &&
        GroovyTokenTypes.mAT.equals(previous.getNode().getElementType()) &&
        (context.getParent() != null && context.getParent().getParent() instanceof GroovyFile ||
         context.getParent() instanceof GrCodeReferenceElement && context.getParent().getParent() instanceof GrAnnotation)) {
      return true;
    }
    return false;
  }

  private static boolean isControlStructure(PsiElement context) {
    final int offset = context.getTextRange().getStartOffset();
    PsiElement prevSibling = context.getPrevSibling();
    if (context.getParent() instanceof GrReferenceElement && prevSibling != null && prevSibling.getNode() != null) {
      ASTNode node = prevSibling.getNode();
      return !TokenSets.DOTS.contains(node.getElementType());
    }
    if (GroovyCompletionUtil.isNewStatement(context, true)) {
      final PsiElement leaf = GroovyCompletionUtil.getLeafByOffset(offset - 1, context);
      if (leaf != null && (leaf.getParent() instanceof GrStatementOwner || leaf.getParent() instanceof GrLabeledStatement)) {
        return true;
      }
    }

    if (context.getParent() != null) {
      PsiElement parent = context.getParent();

      if (parent instanceof GrExpression &&
          parent.getParent() instanceof GroovyFile) {
        return true;
      }

      if (parent instanceof GrReferenceExpression) {

        PsiElement superParent = parent.getParent();

        if (superParent instanceof GrStatementOwner ||
            superParent instanceof GrLabeledStatement ||
            superParent instanceof GrControlStatement ||
            superParent instanceof GrMethodCall) {
          return true;
        }
      }

      return false;
    }

    return false;
  }

  private static boolean inCaseSection(PsiElement context) {
    if (context.getParent() instanceof GrReferenceExpression &&
        context.getParent().getParent() instanceof GrCaseSection) {
      return true;
    }

    final GrSwitchStatement switchStatement = PsiTreeUtil.getParentOfType(context, GrSwitchStatement.class, true, GrCodeBlock.class);
    if (switchStatement == null) return false;

    final GrExpression condition = switchStatement.getCondition();
    return condition == null || !PsiTreeUtil.isAncestor(condition, context, false);
  }

  private static boolean afterTry(PsiElement context) {
    if (context != null &&
        GroovyCompletionUtil.nearestLeftSibling(context) instanceof GrTryCatchStatement) {
      GrTryCatchStatement tryStatement = (GrTryCatchStatement) GroovyCompletionUtil.nearestLeftSibling(context);
      if (tryStatement == null) return false;
      if (tryStatement.getFinallyClause() == null) {
        return true;
      }
    }
    if (context != null &&
        GroovyCompletionUtil.nearestLeftSibling(context) instanceof PsiErrorElement &&
        GroovyCompletionUtil.nearestLeftSibling(context).getPrevSibling() instanceof GrTryCatchStatement) {
      GrTryCatchStatement tryStatement = (GrTryCatchStatement) GroovyCompletionUtil.nearestLeftSibling(context).getPrevSibling();
      if (tryStatement == null) return false;
      if (tryStatement.getFinallyClause() == null) {
        return true;
      }
    }
    if (context != null &&
        (context.getParent() instanceof GrReferenceExpression || context.getParent() instanceof PsiErrorElement) &&
        GroovyCompletionUtil.nearestLeftSibling(context.getParent()) instanceof GrTryCatchStatement) {
      GrTryCatchStatement tryStatement = (GrTryCatchStatement) GroovyCompletionUtil.nearestLeftSibling(context.getParent());
      if (tryStatement == null) return false;
      if (tryStatement.getFinallyClause() == null) {
        return true;
      }
    }

    if (context != null &&
        (context.getParent() instanceof GrReferenceExpression) &&
        (context.getParent().getParent() instanceof GrMethodCall) &&
        GroovyCompletionUtil.nearestLeftSibling(context.getParent().getParent()) instanceof GrTryCatchStatement) {
      GrTryCatchStatement tryStatement = (GrTryCatchStatement) GroovyCompletionUtil.nearestLeftSibling(context.getParent().getParent());
      if (tryStatement == null) return false;
      if (tryStatement.getFinallyClause() == null) {
        return true;
      }
    }

    return false;
  }

  private static boolean afterIfOrElse(PsiElement context) {
    if (context.getParent() != null &&
        GroovyCompletionUtil.nearestLeftSibling(context.getParent()) instanceof GrIfStatement) {
      return true;
    }

    if (context.getParent() != null &&
        GroovyCompletionUtil.nearestLeftSibling(context.getParent()) instanceof PsiErrorElement &&
        GroovyCompletionUtil.nearestLeftSibling(GroovyCompletionUtil.nearestLeftSibling(context.getParent())) instanceof GrIfStatement) {
      return true;
    }

    if (context.getParent() != null &&
        GroovyCompletionUtil.nearestLeftSibling(context) != null &&
        GroovyCompletionUtil.nearestLeftSibling(context).getPrevSibling() instanceof GrIfStatement) {
      GrIfStatement statement = (GrIfStatement) GroovyCompletionUtil.nearestLeftSibling(context).getPrevSibling();
      if (statement.getElseBranch() == null) {
        return true;
      }
    }
    if (context.getParent() != null &&
        context.getParent().getParent() instanceof GrCommandArgumentList &&
        context.getParent().getParent().getParent().getParent() instanceof GrIfStatement) {
      GrIfStatement statement = (GrIfStatement) context.getParent().getParent().getParent().getParent();
      if (statement.getElseBranch() == null) {
        return true;
      }
    }
    return false;
  }

  private static boolean afterAbstractMethod(PsiElement context, boolean acceptAnnotationMethods, boolean skipNLs) {
    PsiElement candidate;
    if (GroovyCompletionUtil.isInTypeDefinitionBody(context)) {
      PsiElement run = context;
      while(!(run.getParent() instanceof GrTypeDefinitionBody)) {
        run = run.getParent();
        assert run != null;
      }
      candidate = PsiUtil.skipWhitespacesAndComments(run.getPrevSibling(), false, skipNLs);
    }
    else {
     candidate = PsiUtil.skipWhitespacesAndComments(PsiTreeUtil.prevLeaf(context), false);
    }
    if (candidate instanceof PsiErrorElement) candidate = candidate.getPrevSibling();

    return candidate instanceof GrMethod &&
           ((GrMethod)candidate).getBlock() == null &&
           (acceptAnnotationMethods || !(candidate instanceof GrAnnotationMethod));
  }

  private static boolean suggestPrimitiveTypes(PsiElement context) {
    if (isInfixOperatorPosition(context)) return false;
    if (isAfterForParameter(context)) return false;

    final PsiElement parent = context.getParent();
    if (parent == null) return false;

    PsiElement previous = PsiImplUtil.realPrevious(parent.getPrevSibling());
    if (parent instanceof GrReferenceElement && parent.getParent() instanceof GrArgumentList) {
      PsiElement prevSibling = context.getPrevSibling();
      if (prevSibling != null && prevSibling.getNode() != null) {
        if (!TokenSets.DOTS.contains(prevSibling.getNode().getElementType())) {
          return true;
        }
      } else if (!(previous != null && GroovyTokenTypes.mAT.equals(previous.getNode().getElementType()))) {
        return true;
      }

    }

    if (GroovyCompletionUtil.isTupleVarNameWithoutTypeDeclared(context)) return true;

    if (previous != null && GroovyTokenTypes.mAT.equals(previous.getNode().getElementType())) {
      return false;
    }
    if (GroovyCompletionUtil.asSimpleVariable(context) ||
        GroovyCompletionUtil.asTypedMethod(context) ||
        GroovyCompletionUtil.asVariableInBlock(context) ||
        asVariableAfterModifiers(context)) {
      return true;
    }
    if ((parent instanceof GrParameter &&
         ((GrParameter)parent).getTypeElementGroovy() == null) ||
        parent instanceof GrReferenceElement &&
        !(parent.getParent() instanceof GrImportStatement) &&
        !(parent.getParent() instanceof GrPackageDefinition) &&
        !(parent.getParent() instanceof GrArgumentList)) {
      PsiElement prevSibling = context.getPrevSibling();
      if (parent instanceof GrReferenceElement && prevSibling != null && prevSibling.getNode() != null) {
        ASTNode node = prevSibling.getNode();
        return !TokenSets.DOTS.contains(node.getElementType());
      } else {
        return true;
      }
    }
    if (PsiImplUtil.realPrevious(parent.getPrevSibling()) instanceof GrModifierList) {
      return true;
    }
    if (PsiImplUtil.realPrevious(context.getPrevSibling()) instanceof GrModifierList) {
      return true;
    }
    return parent instanceof GrExpression &&
           parent.getParent() instanceof GroovyFile &&
           GroovyCompletionUtil.isNewStatement(context, false);
  }

  private static boolean asVariableAfterModifiers(PsiElement context) {
    final PsiElement parent = context.getParent();
    if (parent instanceof GrVariable && context == ((GrVariable)parent).getNameIdentifierGroovy()) {
      final PsiElement decl = parent.getParent();
      if (decl instanceof GrVariableDeclaration &&
          !((GrVariableDeclaration)decl).isTuple() &&
          ((GrVariableDeclaration)decl).getTypeElementGroovy() == null) {
        return true;
      }
    }

    return false;
  }

  private static boolean isInfixOperatorPosition(PsiElement context) {
    if (context.getParent() != null &&
        context.getParent() instanceof GrReferenceExpression &&
        context.getParent().getParent() != null &&
        context.getParent().getParent() instanceof GrCommandArgumentList) {
      return true;
    }
    if (GroovyCompletionUtil.nearestLeftSibling(context) instanceof PsiErrorElement &&
        GroovyCompletionUtil.endsWithExpression(GroovyCompletionUtil.nearestLeftSibling(context).getPrevSibling())) {
      return true;
    }
    if (context.getParent() instanceof GrReferenceExpression &&
        GroovyCompletionUtil.nearestLeftLeaf(context) instanceof PsiErrorElement &&
        GroovyCompletionUtil.endsWithExpression(GroovyCompletionUtil.nearestLeftLeaf(context).getPrevSibling())) {
      return true;
    }
    if (context.getParent() instanceof PsiErrorElement &&
        GroovyCompletionUtil.endsWithExpression(GroovyCompletionUtil.nearestLeftSibling(context.getParent()))) {
      return true;
    }

    return false;
  }

  private static boolean suggestModifiers(PsiElement context) {
    if (GroovyCompletionUtil.asSimpleVariable(context) ||
        GroovyCompletionUtil.asTypedMethod(context) ||
        GroovyCompletionUtil.isNewStatementInScript(context)) {
      return true;
    }
    if (GroovyCompletionUtil.isFirstElementAfterPossibleModifiersInVariableDeclaration(context, false) &&
        !PsiJavaPatterns.psiElement().afterLeaf("def").accepts(context)) {
      return true;
    }

    if (PsiJavaPatterns.psiElement().afterLeaf(MODIFIERS).accepts(context) || PsiJavaPatterns.psiElement().afterLeaf("synchronized").accepts(context)) {
      return true;
    }

    final PsiElement contextParent = context.getParent();
    if (contextParent instanceof GrReferenceElement && contextParent.getParent() instanceof GrTypeElement) {
      PsiElement parent = contextParent.getParent().getParent();
      if (parent instanceof GrVariableDeclaration &&
          (parent.getParent() instanceof GrTypeDefinitionBody || parent.getParent() instanceof GroovyFile) || parent instanceof GrMethod) {
        return true;
      }
    }
    if (contextParent instanceof GrField) {
      final GrVariable variable = (GrVariable)contextParent;
      if (variable.getTypeElementGroovy() == null) {
        return true;
      }
    }
    if (contextParent instanceof GrExpression &&
        contextParent.getParent() instanceof GroovyFile &&
        GroovyCompletionUtil.isNewStatement(context, false)) {
      return true;
    }
    if (context.getTextRange().getStartOffset() == 0 && !(context instanceof OuterLanguageElement)) {
      return true;
    }
    return contextParent instanceof GrExpression &&
           contextParent.getParent() instanceof GrApplicationStatement &&
           contextParent.getParent().getParent() instanceof GroovyFile &&
           GroovyCompletionUtil.isNewStatement(context, false);
  }

  public static boolean suggestFinalDef(PsiElement context) {
    if (GroovyCompletionUtil.asSimpleVariable(context) ||
        GroovyCompletionUtil.asTypedMethod(context) ||
        GroovyCompletionUtil.asVariableInBlock(context) ||
        GroovyCompletionUtil.isNewStatementInScript(context) && !GroovyCompletionUtil.isReferenceElementInNewExpr(context) ||
        GroovyCompletionUtil.isTypelessParameter(context) ||
        GroovyCompletionUtil.isCodeReferenceElementApplicableToModifierCompletion(context)) {
      return true;
    }
    if (PsiImplUtil.realPrevious(context.getParent().getPrevSibling()) instanceof GrModifierList) {
      return true;
    }
    if (PsiImplUtil.realPrevious(context.getPrevSibling()) instanceof GrModifierList) {
      return true;
    }
    return context.getParent() instanceof GrExpression &&
        context.getParent().getParent() instanceof GroovyFile &&
        GroovyCompletionUtil.isNewStatement(context, false);
  }
}
