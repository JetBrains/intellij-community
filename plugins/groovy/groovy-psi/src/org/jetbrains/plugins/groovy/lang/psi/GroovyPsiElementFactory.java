// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @NonNls public static final String DUMMY_FILE_NAME = "DUMMY__1234567890_DUMMYYYYYY___";

  @NotNull
  public abstract GrCodeReferenceElement createCodeReferenceElementFromClass(@NotNull PsiClass aClass);

  @NotNull
  public abstract GrReferenceExpression createThisExpression(@Nullable PsiClass psiClass);

  @NotNull
  public final GrBlockStatement createBlockStatement(GrStatement... statements) {
    StringBuilder text = new StringBuilder();
    text.append("{\n");
    for (GrStatement statement : statements) {
      text.append(statement.getText()).append("\n");
    }
    text.append("}");
    return createBlockStatementFromText(text.toString(), null);
  }

  @NotNull
  public abstract GrBlockStatement createBlockStatementFromText(@NlsSafe @NotNull String text, @Nullable PsiElement context);

  @NotNull
  public abstract GrModifierList createModifierList(@NlsSafe @NotNull CharSequence text);

  @NotNull
  public abstract GrCaseSection createSwitchSection(@NlsSafe @NotNull String text);

  @NotNull
  public static GroovyPsiElementFactory getInstance(@NotNull Project project) {
    return project.getService(GroovyPsiElementFactory.class);
  }

  /**
   * Creates an empty class initializer block.
   *
   * @return the created initializer block instance.
   * @throws IncorrectOperationException in case of an internal error.
   */
  @Override
  @NotNull
  public abstract GrClassInitializer createClassInitializer() throws IncorrectOperationException;

  /**
   * @param qName
   * @param isStatic
   * @param isOnDemand
   * @param alias
   * @return import statement for given class
   */
  @NotNull
  public abstract GrImportStatement createImportStatementFromText(@NlsSafe @NotNull String qName, boolean isStatic, boolean isOnDemand, @Nullable String alias);

  @NotNull
  public abstract GrImportStatement createImportStatementFromText(@NlsSafe @NotNull String text);

  @NotNull
  public abstract GrImportStatement createImportStatement(@NlsSafe @NotNull String qname,
                                                          boolean isStatic,
                                                          boolean isOnDemand,
                                                          @NlsSafe @Nullable String alias,
                                                          @Nullable PsiElement context);

  @NotNull
  public abstract PsiElement createWhiteSpace();

  @NotNull
  public abstract PsiElement createLineTerminator(int length);

  @NotNull
  public abstract PsiElement createLineTerminator(@NlsSafe @NotNull String text);

  @NotNull
  public abstract GrArgumentList createExpressionArgumentList(GrExpression... expressions);

  @NotNull
  public abstract GrNamedArgument createNamedArgument(@NlsSafe @NotNull String name, @NotNull GrExpression expression);

  @NotNull
  public abstract GrStatement createStatementFromText(@NlsSafe @NotNull CharSequence text);

  @NotNull
  public abstract GrStatement createStatementFromText(@NlsSafe @NotNull CharSequence text, @Nullable PsiElement context);

  @NotNull
  public abstract GrMethodCallExpression createMethodCallByAppCall(@NotNull GrApplicationStatement callExpr);

  @NotNull
  public abstract GrReferenceExpression createReferenceExpressionFromText(@NlsSafe @NotNull String exprText);

  @NotNull
  public abstract GrReferenceExpression createReferenceExpressionFromText(@NlsSafe @NotNull String idText, @Nullable PsiElement context) ;

  @NotNull
  public abstract GrReferenceExpression createReferenceElementForClass(@NotNull PsiClass clazz);

  @NotNull
  public GrCodeReferenceElement createCodeReference(@NlsSafe @NotNull String text) {
    return createCodeReference(text, null);
  }

  @NotNull
  public abstract GrCodeReferenceElement createCodeReference(@NlsSafe @NotNull String text, @Nullable PsiElement context);

  @NotNull
  public GrExpression createExpressionFromText(@NlsSafe @NotNull CharSequence exprText) {
    return createExpressionFromText(exprText.toString(), null);
  }

  @Override
  @NotNull
  public abstract GrExpression createExpressionFromText(@NlsSafe @NotNull String exprText, @Nullable PsiElement context);

  @NotNull
  public abstract GrVariableDeclaration createFieldDeclaration(@NlsSafe String @NotNull [] modifiers, @NlsSafe @NotNull String identifier, @Nullable GrExpression initializer, @Nullable PsiType type);

  @NotNull
  public abstract GrVariableDeclaration createFieldDeclarationFromText(@NlsSafe @NotNull String text);

  @NotNull
  public abstract GrVariableDeclaration createVariableDeclaration(@NlsSafe String @Nullable [] modifiers, @Nullable GrExpression initializer, @Nullable PsiType type, @NlsSafe String... identifiers);

  @NotNull
  public abstract GrVariableDeclaration createVariableDeclaration(@NlsSafe String @Nullable [] modifiers, @NlsSafe @Nullable String initializer, @Nullable PsiType type, @NlsSafe String... identifiers);

  @NotNull
  public abstract GrEnumConstant createEnumConstantFromText(@NlsSafe @NotNull String text);

  @NotNull
  public abstract PsiElement createReferenceNameFromText(@NlsSafe @NotNull String idText);

  @NotNull
  public abstract PsiElement createDocMemberReferenceNameFromText(@NlsSafe @NotNull String idText);

  @NotNull
  public abstract GrDocMemberReference createDocMemberReferenceFromText(@NlsSafe @NotNull String className, @NotNull String text);

  @NotNull
  public abstract GrDocReferenceElement createDocReferenceElementFromFQN(@NlsSafe @NotNull String qName);

  @NotNull
  public abstract GrTopStatement createTopElementFromText(@NlsSafe @NotNull String text);

  @NotNull
  public abstract GrClosableBlock createClosureFromText(@NlsSafe @NotNull String text, @Nullable PsiElement context);

  @NotNull
  public GrClosableBlock createClosureFromText(@NlsSafe @NotNull String s) throws IncorrectOperationException {
    return createClosureFromText(s, null);
  }

  @NotNull
  public abstract GrLambdaExpression createLambdaFromText(@NlsSafe @NotNull String text, @Nullable PsiElement context);

  @NotNull
  public GrLambdaExpression createLambdaFromText(@NlsSafe @NotNull String text) {
    return createLambdaFromText(text, null);
  }

  @NotNull
  public GrParameter createParameter(@NlsSafe @NotNull String name, @Nullable String typeText, @Nullable GroovyPsiElement context) throws IncorrectOperationException {
    return createParameter(name, typeText, null, context);
  }

  @NotNull
  public abstract GrParameter createParameter(@NlsSafe @NotNull String name,
                                              @NlsSafe @Nullable String typeText,
                                              @NlsSafe @Nullable String initializer,
                                              @Nullable GroovyPsiElement context,
                                              String... modifiers) throws IncorrectOperationException;

  @NotNull
  public abstract GrTypeDefinition createTypeDefinition(@NlsSafe @NotNull String text) throws IncorrectOperationException;

  @NotNull
  public abstract GrTypeElement createTypeElement(@NotNull PsiType type) throws IncorrectOperationException;

  @NotNull
  public GrTypeElement createTypeElement(@NlsSafe @NotNull String typeText) throws IncorrectOperationException {
    return createTypeElement(typeText, null);
  }

  @NotNull
  public abstract GrTypeElement createTypeElement(@NlsSafe @NotNull String typeText, @Nullable PsiElement context);

  @NotNull
  public abstract GrParenthesizedExpression createParenthesizedExpr(@NotNull GrExpression expression, @Nullable PsiElement context);

  @NotNull
  public abstract PsiElement createStringLiteralForReference(@NlsSafe @NotNull String text);

  @NotNull
  public abstract PsiElement createModifierFromText(@NlsSafe @NotNull String name);

  @NotNull
  public abstract GrCodeBlock createMethodBodyFromText(@NlsSafe @NotNull String text);

  @NotNull
  public abstract GrVariableDeclaration createSimpleVariableDeclaration(@NlsSafe @NotNull String name, @NlsSafe @NotNull String typeText);

  @NotNull
  public abstract PsiElement createDotToken(@NlsSafe @NotNull String newDot);

  @Override
  @NotNull
  public abstract GrMethod createMethodFromText(@NlsSafe String methodText, @Nullable PsiElement context);

  @NotNull
  @Override
  public abstract GrAnnotation createAnnotationFromText(@NlsSafe @NotNull String annotationText, @Nullable PsiElement context) throws IncorrectOperationException;

  @NotNull
  public abstract GrAnnotationNameValuePair createAnnotationAttribute(@NlsSafe @NotNull String text, @Nullable PsiElement context);

  @NotNull
  public abstract GrMethod createMethodFromSignature(@NlsSafe @NotNull String name, @NotNull GrSignature signature);

  @NotNull
  public GrMethod createMethodFromText(@NlsSafe @NotNull CharSequence methodText) {
    return createMethodFromText(methodText.toString(), null);
  }

  @NotNull
  public abstract GrAnnotation createAnnotationFromText(@NlsSafe @NotNull String annoText);

  @NotNull
  public abstract GroovyFile createGroovyFile(@NlsSafe @NotNull CharSequence idText, boolean isPhysical, @Nullable PsiElement context);

  @NotNull
  public abstract GrMethod createMethodFromText(@NlsSafe @NotNull String modifier, @NlsSafe @NotNull String name, @NlsSafe @Nullable String type, @NlsSafe String @NotNull [] paramTypes, @Nullable PsiElement context);

  @NotNull
  public abstract GrMethod createConstructorFromText(@NlsSafe @NotNull String constructorName,
                                                     @NlsSafe String @NotNull [] paramTypes,
                                                     @NlsSafe String @NotNull [] paramNames,
                                                     @NlsSafe @Nullable String body,
                                                     @Nullable PsiElement context);

  @NotNull
  public GrMethod createConstructorFromText(@NlsSafe @NotNull String constructorName, @NlsSafe String @NotNull [] paramTypes, String @NotNull [] paramNames, @Nullable String body) {
    return createConstructorFromText(constructorName, paramTypes, paramNames, body, null);
  }

  @NotNull
  public abstract GrMethod createConstructorFromText(@NlsSafe String constructorName, @NlsSafe CharSequence constructorText, @Nullable PsiElement context);

  @Override
  @NotNull
  public abstract GrDocComment createDocCommentFromText(@NlsSafe @NotNull String text) ;

  @NotNull
  public abstract GrConstructorInvocation createConstructorInvocation(@NlsSafe @NotNull String text);

  @NotNull
  public abstract GrConstructorInvocation createConstructorInvocation(@NlsSafe @NotNull String text, @Nullable PsiElement context);

  @NotNull
  public abstract PsiReferenceList createThrownList(PsiClassType @NotNull [] exceptionTypes);

  @NotNull
  public abstract GrCatchClause createCatchClause(@NotNull PsiClassType type, @NlsSafe @NotNull String parameterName);

  @NotNull
  public abstract GrArgumentList createArgumentList();

  @NotNull
  public abstract GrArgumentList createArgumentListFromText(@NlsSafe @NotNull String argListText);

  @NotNull
  public abstract GrExtendsClause createExtendsClause();

  @NotNull
  public abstract GrImplementsClause createImplementsClause();

  @NotNull
  public abstract GrLiteral createLiteralFromValue(@Nullable Object value);

  @Override
  @NotNull
  public abstract GrMethod createMethod(@NlsSafe @NotNull String name, @Nullable PsiType returnType) throws IncorrectOperationException;

  @Override
  @NotNull
  public abstract GrMethod createMethod(@NlsSafe @NotNull String name, @Nullable PsiType returnType, @Nullable PsiElement context) throws IncorrectOperationException;

  @NotNull
  @Override
  public abstract GrMethod createConstructor();

  @NotNull
  @Override
  public abstract GrParameter createParameter(@NlsSafe @NotNull String name, @Nullable PsiType type) throws IncorrectOperationException;

  @NotNull
  @Override
  public abstract GrField createField(@NlsSafe @NotNull String name, @NotNull PsiType type) throws IncorrectOperationException;

  @NotNull
  public abstract GrTraitTypeDefinition createTrait(@NlsSafe @NotNull String name);

  @NotNull
  public abstract GrTraitTypeDefinition createRecord(@NlsSafe @NotNull String name);
}
