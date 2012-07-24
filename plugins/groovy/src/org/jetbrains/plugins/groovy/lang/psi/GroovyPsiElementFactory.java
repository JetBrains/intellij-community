/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTag;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrExtendsClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrImplementsClause;
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

  public abstract GrCodeReferenceElement createCodeReferenceElementFromClass(PsiClass aClass);

  public abstract GrCodeReferenceElement createCodeReferenceElementFromText(String text);

  public abstract GrThisReferenceExpression createThisExpression(PsiManager manager, PsiClass psiClass);

  public abstract GrBlockStatement createBlockStatementFromText(String text, @Nullable PsiElement context);

  public abstract GrModifierList createModifierList(String text);

  public static GroovyPsiElementFactory getInstance(Project project) {
    return ServiceManager.getService(project, GroovyPsiElementFactory.class);
  }

  /**
   * Creates an empty class initializer block.
   *
   * @return the created initializer block instance.
   * @throws IncorrectOperationException in case of an internal error.
   */
  @NotNull
  public abstract GrClassInitializer createClassInitializer() throws IncorrectOperationException;

  /**
   * @param qName
   * @param isStatic
   * @param isOnDemand
   * @param alias
   * @return import statement for given class
   */
  public abstract GrImportStatement createImportStatementFromText(String qName, boolean isStatic, boolean isOnDemand, @Nullable String alias);

  public abstract GrImportStatement createImportStatementFromText(String text);

  public abstract PsiElement createWhiteSpace();

  @NotNull
  public abstract PsiElement createLineTerminator(int length);

  @NotNull
  public abstract PsiElement createLineTerminator(String text);

  public abstract GrArgumentList createExpressionArgumentList(GrExpression... expressions);

  public abstract GrNamedArgument createNamedArgument(String name, GrExpression expression);

  public abstract GrStatement createStatementFromText(String text);
  public abstract GrStatement createStatementFromText(String text, @Nullable PsiElement context);

  public abstract GrBlockStatement createBlockStatement(GrStatement... statements);

  public abstract GrMethodCallExpression createMethodCallByAppCall(GrApplicationStatement callExpr);

  public abstract GrReferenceExpression createReferenceExpressionFromText(String exprText);

  public abstract GrReferenceExpression createReferenceExpressionFromText(String idText, PsiElement context) ;

  public abstract GrReferenceExpression createReferenceElementForClass(PsiClass clazz);

  public GrCodeReferenceElement createReferenceElementFromText(String refName) {
    return createReferenceElementFromText(refName, null);
  }

  public abstract GrCodeReferenceElement createReferenceElementFromText(String refName, @Nullable PsiElement context);

  public GrExpression createExpressionFromText(String exprText) {
    return createExpressionFromText(exprText, null);
  }

  @NotNull
  public abstract GrExpression createExpressionFromText(@NotNull String exprText, @Nullable PsiElement context);

  public abstract GrVariableDeclaration createFieldDeclaration(String[] modifiers, String identifier, @Nullable GrExpression initializer, @Nullable PsiType type);
  public abstract GrVariableDeclaration createFieldDeclarationFromText(String text);

  public abstract GrVariableDeclaration createVariableDeclaration(@Nullable String[] modifiers, @Nullable GrExpression initializer, @Nullable PsiType type, String... identifiers);

  public abstract GrEnumConstant createEnumConstantFromText(String text);

  @NotNull
  public abstract PsiElement createReferenceNameFromText(String idText);

  public abstract PsiElement createDocMemberReferenceNameFromText(String idText);

  public abstract GrDocMemberReference createDocMemberReferenceFromText(String className, String text);

  public abstract GrDocReferenceElement createDocReferenceElementFromFQN(String qName);

  public abstract GrTopStatement createTopElementFromText(String text);

  public abstract GrClosableBlock createClosureFromText(String text, @Nullable PsiElement context);

  public GrClosableBlock createClosureFromText(String s) throws IncorrectOperationException {
    return createClosureFromText(s, null);
  }

  public GrParameter createParameter(String name, @Nullable String typeText, @Nullable GroovyPsiElement context) throws IncorrectOperationException {
    return createParameter(name, typeText, null, context);
  }

  public abstract GrParameter createParameter(String name,
                                              @Nullable String typeText,
                                              @Nullable String initializer,
                                              @Nullable GroovyPsiElement context) throws IncorrectOperationException;

  public abstract GrCodeReferenceElement createTypeOrPackageReference(String qName);

  public abstract GrTypeDefinition createTypeDefinition(String text) throws IncorrectOperationException;

  public abstract GrTypeElement createTypeElement(PsiType type) throws IncorrectOperationException;

  @NotNull
  public GrTypeElement createTypeElement(String typeText) throws IncorrectOperationException {
    return createTypeElement(typeText, null);
  }

  public abstract GrTypeElement createTypeElement(String typeText, @Nullable PsiElement context);

  public abstract GrParenthesizedExpression createParenthesizedExpr(GrExpression expression);

  public abstract PsiElement createStringLiteralForReference(String text);

  public abstract PsiElement createModifierFromText(String name);

  public abstract GrCodeBlock createMethodBodyFromText(String text);

  public abstract GrVariableDeclaration createSimpleVariableDeclaration(String name, String typeText);

  public abstract GrReferenceElement createPackageReferenceElementFromText(String newPackageName);

  public abstract PsiElement createDotToken(String newDot);

  @NotNull
  public abstract GrMethod createMethodFromText(String methodText, @Nullable PsiElement context);

  @NotNull
  @Override
  public abstract GrAnnotation createAnnotationFromText(@NotNull @NonNls String annotationText, @Nullable PsiElement context) throws IncorrectOperationException;

  public abstract GrMethod createMethodFromSignature(String name, GrClosureSignature signature);

  public GrMethod createMethodFromText(String methodText) {
    return createMethodFromText(methodText, null);
  }

  public abstract GrAnnotation createAnnotationFromText(String annoText);

  public abstract GroovyFile createGroovyFile(String text, boolean isPhysical, @Nullable PsiElement context);

  public abstract GrMethod createMethodFromText(String modifier, String name, String type, String[] paramTypes, @Nullable PsiElement context);

  public abstract GrMethod createConstructorFromText(@NotNull String constructorName,
                                                     String[] paramTypes,
                                                     String[] paramNames,
                                                     String body,
                                                     @Nullable PsiElement context);

  public GrMethod createConstructorFromText(@NotNull String constructorName, String[] paramTypes, String[] paramNames, String body) {
    return createConstructorFromText(constructorName, paramTypes, paramNames, body, null);
  }

  public abstract GrMethod createConstructorFromText(String constructorName, String text, @Nullable PsiElement context);

  public abstract GrLabel createLabel(@NotNull String name);

  public abstract GrDocComment createDocCommentFromText(String text) ;

  public abstract GrDocTag createDocTagFromText(String text) ;

  public abstract GrConstructorInvocation createConstructorInvocation(String text);
  public abstract GrConstructorInvocation createConstructorInvocation(String text, PsiElement context);

  public abstract PsiReferenceList createThrownList(PsiClassType[] exceptionTypes);

  public abstract GrCatchClause createCatchClause(PsiClassType type, String parameterName);

  public abstract GrArgumentList createArgumentList();

  public abstract GrArgumentList createArgumentListFromText(String argListText);

  public abstract GrExtendsClause createExtendsClause();

  public abstract GrImplementsClause createImplementsClause();

  public abstract GrLiteral createLiteralFromValue(@Nullable Object value);

  @NotNull
  public abstract GrMethod createMethod(@NotNull @NonNls String name, @Nullable PsiType returnType) throws IncorrectOperationException;

  @NotNull
  public abstract GrParameter createParameter(@NotNull @NonNls String name, @Nullable PsiType type) throws IncorrectOperationException;

}
