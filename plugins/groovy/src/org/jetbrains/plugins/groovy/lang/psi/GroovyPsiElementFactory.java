/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMemberReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
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
public abstract class GroovyPsiElementFactory {

  public static GroovyPsiElementFactory getInstance(Project project) {
    return ServiceManager.getService(project, GroovyPsiElementFactory.class);
  }

  /**
   * @param qName
   * @param isStatic
   * @param isOnDemand
   * @param alias
   * @return import statement for given class
   */
  public abstract GrImportStatement createImportStatementFromText(String qName, boolean isStatic, boolean isOnDemand, String alias);

  public abstract GrImportStatement createImportStatementFromText(String text);

  public abstract PsiElement createWhiteSpace();

  @NotNull
  public abstract PsiElement createLineTerminator(int length);

  public abstract GrArgumentList createExpressionArgumentList(GrExpression... expressions);

  public abstract GrNamedArgument createNamedArgument(String name, GrExpression expression);

  public abstract GrStatement createStatementFromText(String text);

  public abstract GrBlockStatement createBlockStatement(GrStatement... statements);

  public abstract GrMethodCallExpression createMethodCallByAppCall(GrApplicationStatement callExpr);

  public abstract GrReferenceExpression createReferenceExpressionFromText(String exprText);

  public abstract GrReferenceExpression createReferenceExpressionFromText(String idText, PsiElement context) ;

  public abstract GrCodeReferenceElement createReferenceElementFromText(String refName);

  public abstract GrExpression createExpressionFromText(String exprText);

  public abstract GrVariableDeclaration createFieldDeclaration(String[] modifiers, String identifier, GrExpression initializer, PsiType type);
  public abstract GrVariableDeclaration createFieldDeclarationFromText(String text);

  public abstract GrVariableDeclaration createVariableDeclaration(String[] modifiers, GrExpression initializer, PsiType type, String... identifiers);

  public abstract GrEnumConstant createEnumConstantFromText(String text);

  @NotNull
  public abstract PsiElement createReferenceNameFromText(String idText);

  public abstract PsiElement createDocMemberReferenceNameFromText(String idText);

  public abstract GrDocMemberReference createDocMemberReferenceFromText(String className, String text);

  public abstract GrDocReferenceElement createDocReferenceElementFromFQN(String qName);

  public abstract GrTopStatement createTopElementFromText(String text);

  public abstract GrClosableBlock createClosureFromText(String s) throws IncorrectOperationException;

  public GrParameter createParameter(String name, @Nullable String typeText, GroovyPsiElement context) throws IncorrectOperationException {
    return createParameter(name, typeText, null, context);
  }

  public abstract GrParameter createParameter(String name,
                                              @Nullable String typeText,
                                              String initializer,
                                              GroovyPsiElement context) throws IncorrectOperationException;

  public abstract GrCodeReferenceElement createTypeOrPackageReference(String qName);

  public abstract GrTypeDefinition createTypeDefinition(String text) throws IncorrectOperationException;

  public abstract GrTypeElement createTypeElement(PsiType type) throws IncorrectOperationException;

  @NotNull
  public abstract GrTypeElement createTypeElement(String typeText) throws IncorrectOperationException;

  public abstract GrParenthesizedExpression createParenthesizedExpr(GrExpression newExpr);

  public abstract PsiElement createStringLiteral(String text);

  public abstract PsiElement createModifierFromText(String name);

  public abstract GrCodeBlock createMethodBodyFromText(String text);

  public abstract GrVariableDeclaration createSimpleVariableDeclaration(String name, String typeText);

  public abstract GrReferenceElement createPackageReferenceElementFromText(String newPackageName);

  public abstract PsiElement createDotToken(String newDot);

  public abstract GrMethod createMethodFromText(String methodText, PsiElement context);

  public GrMethod createMethodFromText(String methodText) {
    return createMethodFromText(methodText, null);
  }

  public abstract GrAnnotation createAnnotationFromText(String annoText);

  public abstract GroovyFile createGroovyFile(String text, boolean isPhisical, PsiElement context);

  public abstract GrMethod createMethodFromText(String modifier, String name, String type, String[] paramTypes, PsiElement context);

  public abstract GrMethod createConstructorFromText(@NotNull String constructorName,
                                                     String[] paramTypes,
                                                     String[] paramNames,
                                                     String body,
                                                     PsiElement context);

  public GrMethod createConstructorFromText(@NotNull String constructorName, String[] paramTypes, String[] paramNames, String body) {
    return createConstructorFromText(constructorName, paramTypes, paramNames, body, null);
  }

  public abstract GrLabel createLabel(@NotNull String name);

  public abstract GrDocComment createDocCommentFromText(String text) ;

  public abstract GrConstructorInvocation createConstructorInvocation(String text);

  public abstract PsiReferenceList createThrownList(PsiClassType[] exceptionTypes);

  public abstract GrCatchClause createCatchClause(PsiClassType type, String parameterName);

  public abstract GrArgumentList createArgumentList();
}
