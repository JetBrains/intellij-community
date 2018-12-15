// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMemberReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
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
  public abstract GrBlockStatement createBlockStatementFromText(@NotNull String text, @Nullable PsiElement context);

  @NotNull
  public abstract GrModifierList createModifierList(@NotNull CharSequence text);

  @NotNull
  public abstract GrCaseSection createSwitchSection(@NotNull String text);

  @NotNull
  public static GroovyPsiElementFactory getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GroovyPsiElementFactory.class);
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
  public abstract GrImportStatement createImportStatementFromText(@NotNull String qName, boolean isStatic, boolean isOnDemand, @Nullable String alias);

  @NotNull
  public abstract GrImportStatement createImportStatementFromText(@NotNull String text);

  @NotNull
  public abstract GrImportStatement createImportStatement(@NotNull String qname,
                                                          boolean isStatic,
                                                          boolean isOnDemand,
                                                          @Nullable String alias,
                                                          @Nullable PsiElement context);

  @NotNull
  public abstract PsiElement createWhiteSpace();

  @NotNull
  public abstract PsiElement createLineTerminator(int length);

  @NotNull
  public abstract PsiElement createLineTerminator(@NotNull String text);

  @NotNull
  public abstract GrArgumentList createExpressionArgumentList(GrExpression... expressions);

  @NotNull
  public abstract GrNamedArgument createNamedArgument(@NotNull String name, @NotNull GrExpression expression);

  @NotNull
  public abstract GrStatement createStatementFromText(@NotNull CharSequence text);

  @NotNull
  public abstract GrStatement createStatementFromText(@NotNull CharSequence text, @Nullable PsiElement context);

  @NotNull
  public abstract GrBlockStatement createBlockStatement(GrStatement... statements);

  @NotNull
  public abstract GrMethodCallExpression createMethodCallByAppCall(@NotNull GrApplicationStatement callExpr);

  @NotNull
  public abstract GrReferenceExpression createReferenceExpressionFromText(@NotNull String exprText);

  @NotNull
  public abstract GrReferenceExpression createReferenceExpressionFromText(@NotNull String idText, @Nullable PsiElement context) ;

  @NotNull
  public abstract GrReferenceExpression createReferenceElementForClass(@NotNull PsiClass clazz);

  @NotNull
  public GrCodeReferenceElement createCodeReference(@NotNull String text) {
    return createCodeReference(text, null);
  }

  @NotNull
  public abstract GrCodeReferenceElement createCodeReference(@NotNull String text, @Nullable PsiElement context);

  @NotNull
  public GrExpression createExpressionFromText(@NotNull CharSequence exprText) {
    return createExpressionFromText(exprText.toString(), null);
  }

  @Override
  @NotNull
  public abstract GrExpression createExpressionFromText(@NotNull String exprText, @Nullable PsiElement context);

  @NotNull
  public abstract GrVariableDeclaration createFieldDeclaration(@NotNull String[] modifiers, @NotNull String identifier, @Nullable GrExpression initializer, @Nullable PsiType type);

  @NotNull
  public abstract GrVariableDeclaration createFieldDeclarationFromText(@NotNull String text);

  @NotNull
  public abstract GrVariableDeclaration createVariableDeclaration(@Nullable String[] modifiers, @Nullable GrExpression initializer, @Nullable PsiType type, String... identifiers);

  @NotNull
  public abstract GrVariableDeclaration createVariableDeclaration(@Nullable String[] modifiers, @Nullable String initializer, @Nullable PsiType type, String... identifiers);

  @NotNull
  public abstract GrEnumConstant createEnumConstantFromText(@NotNull String text);

  @NotNull
  public abstract PsiElement createReferenceNameFromText(@NotNull String idText);

  @NotNull
  public abstract PsiElement createDocMemberReferenceNameFromText(@NotNull String idText);

  @NotNull
  public abstract GrDocMemberReference createDocMemberReferenceFromText(@NotNull String className, @NotNull String text);

  @NotNull
  public abstract GrDocReferenceElement createDocReferenceElementFromFQN(@NotNull String qName);

  @NotNull
  public abstract GrTopStatement createTopElementFromText(@NotNull String text);

  @NotNull
  public abstract GrClosableBlock createClosureFromText(@NotNull String text, @Nullable PsiElement context);

  @NotNull
  public GrClosableBlock createClosureFromText(@NotNull String s) throws IncorrectOperationException {
    return createClosureFromText(s, null);
  }

  @NotNull
  public GrParameter createParameter(@NotNull String name, @Nullable String typeText, @Nullable GroovyPsiElement context) throws IncorrectOperationException {
    return createParameter(name, typeText, null, context);
  }

  @NotNull
  public abstract GrParameter createParameter(@NotNull String name,
                                              @Nullable String typeText,
                                              @Nullable String initializer,
                                              @Nullable GroovyPsiElement context,
                                              String... modifiers) throws IncorrectOperationException;

  @NotNull
  public abstract GrCodeReferenceElement createTypeOrPackageReference(@NotNull String qName);

  @NotNull
  public abstract GrTypeDefinition createTypeDefinition(@NotNull String text) throws IncorrectOperationException;

  @NotNull
  public abstract GrTypeElement createTypeElement(@NotNull PsiType type) throws IncorrectOperationException;

  @NotNull
  public GrTypeElement createTypeElement(@NotNull String typeText) throws IncorrectOperationException {
    return createTypeElement(typeText, null);
  }

  @NotNull
  public abstract GrTypeElement createTypeElement(@NotNull String typeText, @Nullable PsiElement context);

  @NotNull
  public abstract GrParenthesizedExpression createParenthesizedExpr(@NotNull GrExpression expression);

  @NotNull
  public abstract PsiElement createStringLiteralForReference(@NotNull String text);

  @NotNull
  public abstract PsiElement createModifierFromText(@NotNull String name);

  @NotNull
  public abstract GrCodeBlock createMethodBodyFromText(@NotNull String text);

  @NotNull
  public abstract GrVariableDeclaration createSimpleVariableDeclaration(@NotNull String name, @NotNull String typeText);

  @NotNull
  public abstract PsiElement createDotToken(@NotNull String newDot);

  @Override
  @NotNull
  public abstract GrMethod createMethodFromText(String methodText, @Nullable PsiElement context);

  @NotNull
  @Override
  public abstract GrAnnotation createAnnotationFromText(@NotNull @NonNls String annotationText, @Nullable PsiElement context) throws IncorrectOperationException;

  @NotNull
  public abstract GrAnnotationNameValuePair createAnnotationAttribute(@NotNull String text, @Nullable PsiElement context);

  @NotNull
  public abstract GrMethod createMethodFromSignature(@NotNull String name, @NotNull GrSignature signature);

  @NotNull
  public GrMethod createMethodFromText(@NotNull CharSequence methodText) {
    return createMethodFromText(methodText.toString(), null);
  }

  @NotNull
  public abstract GrAnnotation createAnnotationFromText(@NotNull String annoText);

  @NotNull
  public abstract GroovyFile createGroovyFile(@NotNull CharSequence idText, boolean isPhysical, @Nullable PsiElement context);

  @NotNull
  public abstract GrMethod createMethodFromText(@NotNull String modifier, @NotNull String name, @Nullable String type, @NotNull String[] paramTypes, @Nullable PsiElement context);

  @NotNull
  public abstract GrMethod createConstructorFromText(@NotNull String constructorName,
                                                     @NotNull String[] paramTypes,
                                                     @NotNull String[] paramNames,
                                                     @Nullable String body,
                                                     @Nullable PsiElement context);

  @NotNull
  public GrMethod createConstructorFromText(@NotNull String constructorName, @NotNull String[] paramTypes, @NotNull String[] paramNames, @Nullable String body) {
    return createConstructorFromText(constructorName, paramTypes, paramNames, body, null);
  }

  @NotNull
  public abstract GrMethod createConstructorFromText(String constructorName, CharSequence constructorText, @Nullable PsiElement context);

  @Override
  @NotNull
  public abstract GrDocComment createDocCommentFromText(@NotNull String text) ;

  @NotNull
  public abstract GrConstructorInvocation createConstructorInvocation(@NotNull String text);

  @NotNull
  public abstract GrConstructorInvocation createConstructorInvocation(@NotNull String text, @Nullable PsiElement context);

  @NotNull
  public abstract PsiReferenceList createThrownList(@NotNull PsiClassType[] exceptionTypes);

  @NotNull
  public abstract GrCatchClause createCatchClause(@NotNull PsiClassType type, @NotNull String parameterName);

  @NotNull
  public abstract GrArgumentList createArgumentList();

  @NotNull
  public abstract GrArgumentList createArgumentListFromText(@NotNull String argListText);

  @NotNull
  public abstract GrExtendsClause createExtendsClause();

  @NotNull
  public abstract GrImplementsClause createImplementsClause();

  @NotNull
  public abstract GrLiteral createLiteralFromValue(@Nullable Object value);

  @Override
  @NotNull
  public abstract GrMethod createMethod(@NotNull @NonNls String name, @Nullable PsiType returnType) throws IncorrectOperationException;

  @Override
  @NotNull
  public abstract GrMethod createMethod(@NotNull @NonNls String name, @Nullable PsiType returnType, @Nullable PsiElement context) throws IncorrectOperationException;

  @NotNull
  @Override
  public abstract GrMethod createConstructor();

  @NotNull
  @Override
  public abstract GrParameter createParameter(@NotNull @NonNls String name, @Nullable PsiType type) throws IncorrectOperationException;

  @NotNull
  @Override
  public abstract GrField createField(@NotNull @NonNls String name, @NotNull PsiType type) throws IncorrectOperationException;

  @NotNull
  public abstract GrTraitTypeDefinition createTrait(@NotNull String name);
}
