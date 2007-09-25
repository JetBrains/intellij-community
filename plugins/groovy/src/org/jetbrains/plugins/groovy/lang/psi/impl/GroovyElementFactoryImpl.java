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

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.*;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.GrBlockStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.refactoring.GroovyRefactoringUtil;

/**
 * @author ven
 */
public class GroovyElementFactoryImpl extends GroovyElementFactory implements ProjectComponent {
  Project myProject;

  public GroovyElementFactoryImpl(Project project) {
    myProject = project;
  }

  private static String DUMMY = "dummy.";

  public PsiElement createReferenceNameFromText(String refName) {
    PsiFile file = createGroovyFile("a." + refName);
    return ((GrReferenceExpression) ((GroovyFileBase) file).getTopStatements()[0]).getReferenceNameElement();
  }

  public GrReferenceExpression createReferenceExpressionFromText(String idText) {
    PsiFile file = createGroovyFile(idText);
    return (GrReferenceExpression) ((GroovyFileBase) file).getTopStatements()[0];
  }

  public GrExpression createExpressionFromText(String text) {
    PsiFile file = createGroovyFile(text);
    assert ((GroovyFileBase) file).getTopStatements()[0] instanceof GrExpression;
    return (GrExpression) ((GroovyFileBase) file).getTopStatements()[0];
  }

  public GrVariableDeclaration createVariableDeclaration(String[] modifiers, String identifier, GrExpression initializer, PsiType type) {
    StringBuffer text = writeModifiers(modifiers);

    if (type != null) {
      type = TypesUtil.unboxPrimitiveTypeWrapper(type);
      final String typeText = getTypeText(type);
      int lastDot = typeText.lastIndexOf('.');
      int idx = 0 < lastDot && lastDot < typeText.length() - 1 ? lastDot + 1 : 0;
      if (Character.isLowerCase(typeText.charAt(idx))) text.append("def ");
      text.append(typeText).append(" ");
    } else {
      text.append("def ");
    }

    text.append(identifier);
    GrExpression expr;

    if (initializer != null) {
      if (initializer instanceof GrApplicationStatement) {
        expr = createMethodCallByAppCall(((GrApplicationStatement) initializer));
      } else {
        expr = initializer;
      }
      text.append("=").append(expr.getText());
    }

    PsiFile file = createGroovyFile(text.toString());
    return ((GrVariableDeclaration) ((GroovyFileBase) file).getTopStatements()[0]);
  }

  public GrVariableDeclaration createFieldDeclaration(String[] modifiers, String identifier, GrExpression initializer, PsiType type) {
    final String varDeclaration = createVariableDeclaration(modifiers, identifier, initializer, type).getText();

    final GroovyFileBase file = (GroovyFileBase) createGroovyFile("class A { " + varDeclaration + "}");
    final GrTypeDefinitionBody body = file.getTypeDefinitions()[0].getBody();
    return (GrVariableDeclaration) body.getMemberDeclarations()[0];
  }

  private StringBuffer writeModifiers(String[] modifiers) {
    StringBuffer text = new StringBuffer();
    if (!(modifiers == null || modifiers.length == 0)) {
      for (String modifier : modifiers) {
        text.append(modifier);
        text.append(" ");
      }
    }
    return text;
  }

  private String getTypeText(PsiType type) {
    final String canonical = type.getCanonicalText();
    return canonical != null ? canonical : type.getPresentableText();
  }

  @Nullable
  public GrTopStatement createTopElementFromText(String text) {
    PsiFile dummyFile = PsiManager.getInstance(myProject).getElementFactory().createFileFromText(DUMMY + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(),
        text);
    return (GrTopStatement) dummyFile.getFirstChild();
  }

  public GrClosableBlock createClosureFromText(String closureText) throws IncorrectOperationException {
    PsiFile psiFile = createDummyFile(closureText);
    ASTNode node = psiFile.getFirstChild().getNode();
    if (node.getElementType() != GroovyElementTypes.CLOSABLE_BLOCK)
      throw new IncorrectOperationException("Invalid all text");
    return (GrClosableBlock) node.getPsi();
  }

  private GroovyFileBase createDummyFile(String s) {
    return (GroovyFileBase) PsiManager.getInstance(myProject).getElementFactory().createFileFromText("__DUMMY." + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(), s);
  }

  public GrParameter createParameter(String name, @Nullable String typeText, GroovyPsiElement context) throws IncorrectOperationException {
    String fileText;
    if (typeText != null) {
      fileText = "def foo(" + typeText + " " + name + ") {}";
    } else {
      fileText = "def foo(" + name + ") {}";
    }
    GroovyFileImpl groovyFile = (GroovyFileImpl) createDummyFile(fileText);
    groovyFile.setContext(context);

    ASTNode node = groovyFile.getFirstChild().getNode();
    if (node.getElementType() != GroovyElementTypes.METHOD_DEFINITION)
      throw new IncorrectOperationException("Invalid all text");
    return ((GrMethod) node.getPsi()).getParameters()[0];
  }

  public GrCodeReferenceElement createTypeOrPackageReference(String qName) {
    final GroovyFileBase file = createDummyFile("def " + qName + " i");
    GrVariableDeclaration varDecl = (GrVariableDeclaration) file.getTopStatements()[0];
    final GrClassTypeElement typeElement = (GrClassTypeElement) varDecl.getTypeElementGroovy();
    assert typeElement != null;
    return typeElement.getReferenceElement();
  }

  public GrTypeDefinition createTypeDefinition(String text) throws IncorrectOperationException {
    final GroovyFileBase file = createDummyFile(text);
    final GrTypeDefinition[] classes = file.getTypeDefinitions();
    if (classes.length != 1) throw new IncorrectOperationException("Incorrect type definition text");
    return classes[0];
  }

  public GrTypeElement createTypeElement(PsiType type) {
    final String typeText = getTypeText(type);
    if (typeText == null) throw new RuntimeException("Cannot create type element: cannot obtain text for type");
    final GroovyFileBase file = createDummyFile("def " + typeText + " someVar");
    GrVariableDeclaration decl = (GrVariableDeclaration) file.getTopStatements()[0];
    return decl.getTypeElementGroovy();
  }

  public GrParenthesizedExpr createParenthesizedExpr(GrExpression newExpr) {
    return ((GrParenthesizedExpr) getInstance(myProject).createExpressionFromText("(" + newExpr.getText() + ")"));
  }

  public PsiElement createStringLiteral(String text) {
    return ((GrReferenceExpression) createDummyFile("a.'" + text + "'").getTopStatements()[0]).getReferenceNameElement();
  }

  public PsiElement createModifierFormText(String name) {
    final GroovyFileBase file = createDummyFile(name + " def foo () {}");
    return file.getTopLevelDefinitions()[0].getFirstChild().getFirstChild();
  }

  public GrCodeBlock createMetodBodyFormText(String text) {
    final GroovyFileBase file = createDummyFile("def foo () {" + text + "}");
    final GrMethod method = (GrMethod) file.getTopLevelDefinitions()[0];
    return method.getBlock();
  }

  public GrVariableDeclaration createSimpleVariableDeclaration(String name, String typeText) {
    GroovyFileBase file = (GroovyFileBase) createGroovyFile("class A { " + typeText + " " + name + "}");
    final GrTypeDefinitionBody body = file.getTypeDefinitions()[0].getBody();
    return (GrVariableDeclaration) body.getMemberDeclarations()[0];
  }

  private PsiFile createGroovyFile(String idText) {
    return createDummyFile(idText);
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return "Groovy Element Factory";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public PsiElement createWhiteSpace() {
    PsiFile dummyFile = PsiManager.getInstance(myProject).getElementFactory().createFileFromText(DUMMY + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(),
        " ");
    return dummyFile.getFirstChild();
  }

  @NotNull
  public PsiElement createLineTerminator() {
    PsiFile dummyFile = PsiManager.getInstance(myProject).getElementFactory().createFileFromText(DUMMY + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(),
        "\n");
    return dummyFile.getFirstChild();
  }

  public GrArgumentList createExpressionArgumentList(GrExpression... expressions) {
    StringBuffer text = new StringBuffer();
    text.append("ven (");
    for (GrExpression expression : expressions) {
      text.append(expression.getText()).append(", ");
    }
    if (expressions.length > 0) {
      text.delete(text.length() - 2, text.length());
    }
    text.append(")");
    PsiFile file = createGroovyFile(text.toString());
    assert file.getChildren()[0] != null && (file.getChildren()[0] instanceof GrMethodCallExpression);
    return (((GrMethodCallExpression) file.getChildren()[0])).getArgumentList();
  }

  public GrBlockStatement createBlockStatement(@NonNls GrStatement... statements) {
    StringBuffer text = new StringBuffer();
    text.append("while (true) { \n");
    for (GrStatement statement : statements) {
      text.append(statement.getText()).append("\n");
    }
    text.append("}");
    PsiFile file = createGroovyFile(text.toString());
    assert file.getChildren()[0] != null && (file.getChildren()[0] instanceof GrWhileStatement);
    return (GrBlockStatement) ((GrWhileStatement) file.getChildren()[0]).getBody();
  }

  public GrMethodCallExpression createMethodCallByAppCall(GrApplicationStatement callExpr) {
    StringBuffer text = new StringBuffer();
    text.append(callExpr.getFunExpression().getText());
    text.append("(");
    for (GrExpression expr : callExpr.getArguments()) {
      text.append(GroovyRefactoringUtil.getUnparenthesizedExpr(expr).getText()).append(", ");
    }
    if (callExpr.getArguments().length > 0) {
      text.delete(text.length() - 2, text.length());
    }
    text.append(")");
    PsiFile file = createGroovyFile(text.toString());
    assert file.getChildren()[0] != null && (file.getChildren()[0] instanceof GrMethodCallExpression);
    return ((GrMethodCallExpression) file.getChildren()[0]);
  }

  public GrImportStatement createImportStatementFromText(String qName, boolean isStatic, boolean isOnDemand) {
    final String text = "import " + (isStatic ? "static " : "") + qName + (isOnDemand ? ".*" : "");

    PsiFile dummyFile = PsiManager.getInstance(myProject).getElementFactory().createFileFromText(DUMMY + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(),
        text);
    return ((GrImportStatement) dummyFile.getFirstChild());
  }


}
