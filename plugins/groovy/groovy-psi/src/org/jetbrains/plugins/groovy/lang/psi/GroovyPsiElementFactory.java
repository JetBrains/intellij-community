// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMemberReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GrLambdaExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTraitTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * @author dimaskin
 */
public abstract class GroovyPsiElementFactory implements JVMElementFactory {

  public static final @NonNls String DUMMY_FILE_NAME = "DUMMY__1234567890_DUMMYYYYYY___";

  public abstract @NotNull GrCodeReferenceElement createCodeReferenceElementFromClass(@NotNull PsiClass aClass);

  public abstract @NotNull GrReferenceExpression createThisExpression(@Nullable PsiClass psiClass);

  public final @NotNull GrBlockStatement createBlockStatement(GrStatement... statements) {
    StringBuilder text = new StringBuilder();
    text.append("{\n");
    for (GrStatement statement : statements) {
      text.append(statement.getText()).append("\n");
    }
    text.append("}");
    return createBlockStatementFromText(text.toString(), null);
  }

  public abstract @NotNull GrBlockStatement createBlockStatementFromText(@NlsSafe @NotNull String text, @Nullable PsiElement context);

  public abstract @NotNull GrModifierList createModifierList(@NlsSafe @NotNull CharSequence text);

  public abstract @NotNull GrCaseSection createSwitchSection(@NlsSafe @NotNull String text);

  public static @NotNull GroovyPsiElementFactory getInstance(@NotNull Project project) {
    return project.getService(GroovyPsiElementFactory.class);
  }

  /**
   * Creates an empty class initializer block.
   *
   * @return the created initializer block instance.
   * @throws IncorrectOperationException in case of an internal error.
   */
  @Override
  public abstract @NotNull GrClassInitializer createClassInitializer() throws IncorrectOperationException;

  /**
   * @return import statement for given class
   */
  public abstract @NotNull GrImportStatement createImportStatementFromText(@NlsSafe @NotNull String qName, boolean isStatic, boolean isOnDemand, @Nullable String alias);

  public abstract @NotNull GrImportStatement createImportStatementFromText(@NlsSafe @NotNull String text);

  public abstract @NotNull GrImportStatement createImportStatement(@NlsSafe @NotNull String qname,
                                                                   boolean isStatic,
                                                                   boolean isOnDemand,
                                                                   @NlsSafe @Nullable String alias,
                                                                   @Nullable PsiElement context);

  public abstract @NotNull PsiElement createWhiteSpace();

  public abstract @NotNull PsiElement createLineTerminator(int length);

  public abstract @NotNull PsiElement createLineTerminator(@NlsSafe @NotNull String text);

  public abstract @NotNull GrArgumentList createExpressionArgumentList(GrExpression... expressions);

  public abstract @NotNull GrNamedArgument createNamedArgument(@NlsSafe @NotNull String name, @NotNull GrExpression expression);

  public abstract @NotNull GrStatement createStatementFromText(@NlsSafe @NotNull CharSequence text);

  public abstract @NotNull GrStatement createStatementFromText(@NlsSafe @NotNull CharSequence text, @Nullable PsiElement context);

  public abstract @NotNull GrMethodCallExpression createMethodCallByAppCall(@NotNull GrApplicationStatement callExpr);

  public abstract @NotNull GrReferenceExpression createReferenceExpressionFromText(@NlsSafe @NotNull String exprText);

  public abstract @NotNull GrReferenceExpression createReferenceExpressionFromText(@NlsSafe @NotNull String idText, @Nullable PsiElement context) ;

  public abstract @NotNull GrReferenceExpression createReferenceElementForClass(@NotNull PsiClass clazz);

  public @NotNull GrCodeReferenceElement createCodeReference(@NlsSafe @NotNull String text) {
    return createCodeReference(text, null);
  }

  public abstract @NotNull GrCodeReferenceElement createCodeReference(@NlsSafe @NotNull String text, @Nullable PsiElement context);

  public @NotNull GrExpression createExpressionFromText(@NlsSafe @NotNull CharSequence exprText) {
    return createExpressionFromText(exprText.toString(), null);
  }

  @Override
  public abstract @NotNull GrExpression createExpressionFromText(@NlsSafe @NotNull String exprText, @Nullable PsiElement context);

  public abstract @NotNull GrVariableDeclaration createFieldDeclaration(@NlsSafe String @NotNull [] modifiers, @NlsSafe @NotNull String identifier, @Nullable GrExpression initializer, @Nullable PsiType type);

  public abstract @NotNull GrVariableDeclaration createFieldDeclarationFromText(@NlsSafe @NotNull String text);

  public abstract @NotNull GrVariableDeclaration createVariableDeclaration(@NlsSafe String @Nullable [] modifiers, @Nullable GrExpression initializer, @Nullable PsiType type, @NlsSafe String... identifiers);

  public abstract @NotNull GrVariableDeclaration createVariableDeclaration(@NlsSafe String @Nullable [] modifiers, @NlsSafe @Nullable String initializer, @Nullable PsiType type, @NlsSafe String... identifiers);

  public abstract @NotNull GrEnumConstant createEnumConstantFromText(@NlsSafe @NotNull String text);

  public abstract @NotNull PsiElement createReferenceNameFromText(@NlsSafe @NotNull String idText);

  public abstract @NotNull PsiElement createDocMemberReferenceNameFromText(@NlsSafe @NotNull String idText);

  public abstract @NotNull GrDocMemberReference createDocMemberReferenceFromText(@NlsSafe @NotNull String className, @NotNull String text);

  public abstract @NotNull GrDocReferenceElement createDocReferenceElementFromFQN(@NlsSafe @NotNull String qName);

  public abstract @NotNull GrTopStatement createTopElementFromText(@NlsSafe @NotNull String text);

  public abstract @NotNull GrClosableBlock createClosureFromText(@NlsSafe @NotNull String text, @Nullable PsiElement context);

  public @NotNull GrClosableBlock createClosureFromText(@NlsSafe @NotNull String s) throws IncorrectOperationException {
    return createClosureFromText(s, null);
  }

  public abstract @NotNull GrLambdaExpression createLambdaFromText(@NlsSafe @NotNull String text, @Nullable PsiElement context);

  public @NotNull GrLambdaExpression createLambdaFromText(@NlsSafe @NotNull String text) {
    return createLambdaFromText(text, null);
  }

  public @NotNull GrParameter createParameter(@NlsSafe @NotNull String name, @Nullable String typeText, @Nullable GroovyPsiElement context) throws IncorrectOperationException {
    return createParameter(name, typeText, null, context);
  }

  public abstract @NotNull GrParameter createParameter(@NlsSafe @NotNull String name,
                                                       @NlsSafe @Nullable String typeText,
                                                       @NlsSafe @Nullable String initializer,
                                                       @Nullable GroovyPsiElement context,
                                                       String... modifiers) throws IncorrectOperationException;

  public abstract @NotNull GrTypeDefinition createTypeDefinition(@NlsSafe @NotNull String text) throws IncorrectOperationException;

  public abstract @NotNull GrTypeElement createTypeElement(@NotNull PsiType type) throws IncorrectOperationException;

  public @NotNull GrTypeElement createTypeElement(@NlsSafe @NotNull String typeText) throws IncorrectOperationException {
    return createTypeElement(typeText, null);
  }

  public abstract @NotNull GrTypeElement createTypeElement(@NlsSafe @NotNull String typeText, @Nullable PsiElement context);

  public abstract @NotNull GrParenthesizedExpression createParenthesizedExpr(@NotNull GrExpression expression, @Nullable PsiElement context);

  public abstract @NotNull PsiElement createStringLiteralForReference(@NlsSafe @NotNull String text);

  public abstract @NotNull PsiElement createModifierFromText(@NlsSafe @NotNull String name);

  public abstract @NotNull GrCodeBlock createMethodBodyFromText(@NlsSafe @NotNull String text);

  public abstract @NotNull GrVariableDeclaration createSimpleVariableDeclaration(@NlsSafe @NotNull String name, @NlsSafe @NotNull String typeText);

  public abstract @NotNull PsiElement createDotToken(@NlsSafe @NotNull String newDot);

  @Override
  public abstract @NotNull GrMethod createMethodFromText(@NlsSafe String methodText, @Nullable PsiElement context);

  @Override
  public abstract @NotNull GrAnnotation createAnnotationFromText(@NlsSafe @NotNull String annotationText, @Nullable PsiElement context) throws IncorrectOperationException;

  public abstract @NotNull GrAnnotationNameValuePair createAnnotationAttribute(@NlsSafe @NotNull String text, @Nullable PsiElement context);

  public abstract @NotNull GrMethod createMethodFromSignature(@NlsSafe @NotNull String name, @NotNull GrSignature signature);

  public @NotNull GrMethod createMethodFromText(@NlsSafe @NotNull CharSequence methodText) {
    return createMethodFromText(methodText.toString(), null);
  }

  public abstract @NotNull GrAnnotation createAnnotationFromText(@NlsSafe @NotNull String annoText);

  public abstract @NotNull GroovyFile createGroovyFile(@NlsSafe @NotNull CharSequence idText, boolean isPhysical, @Nullable PsiElement context);

  public abstract @NotNull GrMethod createMethodFromText(@NlsSafe @NotNull String modifier, @NlsSafe @NotNull String name, @NlsSafe @Nullable String type, @NlsSafe String @NotNull [] paramTypes, @Nullable PsiElement context);

  public abstract @NotNull GrMethod createConstructorFromText(@NlsSafe @NotNull String constructorName,
                                                              @NlsSafe String @NotNull [] paramTypes,
                                                              @NlsSafe String @NotNull [] paramNames,
                                                              @NlsSafe @Nullable String body,
                                                              @Nullable PsiElement context);

  public @NotNull GrMethod createConstructorFromText(@NlsSafe @NotNull String constructorName, @NlsSafe String @NotNull [] paramTypes, String @NotNull [] paramNames, @Nullable String body) {
    return createConstructorFromText(constructorName, paramTypes, paramNames, body, null);
  }

  public abstract @NotNull GrMethod createConstructorFromText(@NlsSafe String constructorName, @NlsSafe CharSequence constructorText, @Nullable PsiElement context);

  @Override
  public abstract @NotNull GrDocComment createDocCommentFromText(@NlsSafe @NotNull String text) ;

  public abstract @NotNull GrConstructorInvocation createConstructorInvocation(@NlsSafe @NotNull String text);

  public abstract @NotNull GrConstructorInvocation createConstructorInvocation(@NlsSafe @NotNull String text, @Nullable PsiElement context);

  public abstract @NotNull PsiReferenceList createThrownList(PsiClassType @NotNull [] exceptionTypes);

  public abstract @NotNull GrCatchClause createCatchClause(@NotNull PsiClassType type, @NlsSafe @NotNull String parameterName);

  public abstract @NotNull GrArgumentList createArgumentList();

  public abstract @NotNull GrArgumentList createArgumentListFromText(@NlsSafe @NotNull String argListText);

  public abstract @NotNull GrExtendsClause createExtendsClause();

  public abstract @NotNull GrImplementsClause createImplementsClause();

  public abstract @NotNull GrLiteral createLiteralFromValue(@Nullable Object value);

  @Override
  public abstract @NotNull GrMethod createMethod(@NlsSafe @NotNull String name, @Nullable PsiType returnType) throws IncorrectOperationException;

  @Override
  public abstract @NotNull GrMethod createMethod(@NlsSafe @NotNull String name, @Nullable PsiType returnType, @Nullable PsiElement context) throws IncorrectOperationException;

  @Override
  public abstract @NotNull GrMethod createConstructor();

  @Override
  public abstract @NotNull GrParameter createParameter(@NlsSafe @NotNull String name, @Nullable PsiType type) throws IncorrectOperationException;

  @Override
  public abstract @NotNull GrField createField(@NlsSafe @NotNull String name, @NotNull PsiType type) throws IncorrectOperationException;

  public abstract @NotNull GrTraitTypeDefinition createTrait(@NlsSafe @NotNull String name);

  public abstract @NotNull GrTraitTypeDefinition createRecord(@NlsSafe @NotNull String name);
}
