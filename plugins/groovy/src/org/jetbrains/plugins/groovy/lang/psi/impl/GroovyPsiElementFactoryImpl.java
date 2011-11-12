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

package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMemberReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTag;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class GroovyPsiElementFactoryImpl extends GroovyPsiElementFactory {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementFactoryImpl");

  Project myProject;

  public GroovyPsiElementFactoryImpl(Project project) {
    myProject = project;
  }

  private static final String DUMMY = "dummy.";

  @NotNull
  public PsiElement createReferenceNameFromText(String refName) {
    PsiFile file = createGroovyFile("a." + refName);
    GrTopStatement statement = ((GroovyFileBase) file).getTopStatements()[0];
    if (!(statement instanceof GrReferenceExpression)) {
      throw new IncorrectOperationException("Incorrect reference name: " + refName);
    }
    final PsiElement element = ((GrReferenceExpression)statement).getReferenceNameElement();
    if (element == null) {
      throw new IncorrectOperationException("Incorrect reference name: " + refName);
    }
    return element;
  }

  public PsiElement createDocMemberReferenceNameFromText(String idText) {
    GrDocMemberReference reference = createDocMemberReferenceFromText("Foo", idText);
    assert reference != null : "DocMemberReference ponts to null";
    return reference.getReferenceNameElement();
  }

  public GrDocMemberReference createDocMemberReferenceFromText(String className, String text) {
    PsiFile file = createGroovyFile("/** @see " + className + "#" + text + " */");
    PsiElement element = file.getFirstChild();
    assert element instanceof GrDocComment;
    GrDocTag tag = PsiTreeUtil.getChildOfType(element, GrDocTag.class);
    assert tag != null : "Doc tag points to null";
    return PsiTreeUtil.getChildOfType(tag, GrDocMemberReference.class);
  }

  public GrDocReferenceElement createDocReferenceElementFromFQN(String qName) {
    PsiFile file = createGroovyFile("/** @see " + qName + " */");
    PsiElement element = file.getFirstChild();
    assert element instanceof GrDocComment;
    GrDocTag tag = PsiTreeUtil.getChildOfType(element, GrDocTag.class);
    assert tag != null : "Doc tag points to null";
    return PsiTreeUtil.getChildOfType(tag, GrDocReferenceElement.class);
  }

  public GrCodeReferenceElement createReferenceElementFromText(String refName, final PsiElement context) {
    PsiFile file = createGroovyFile("(" + refName + " " + ")foo", false, context);
    GrTypeElement typeElement = ((GrTypeCastExpression) ((GroovyFileBase) file).getTopStatements()[0]).getCastTypeElement();
    return ((GrClassTypeElement) typeElement).getReferenceElement();
  }

  public GrReferenceExpression createReferenceExpressionFromText(String idText) {
    PsiFile file = createGroovyFile(idText);
    return (GrReferenceExpression) ((GroovyFileBase) file).getTopStatements()[0];
  }

  public GrReferenceExpression createReferenceExpressionFromText(String idText, PsiElement context) {
    PsiFile file = createGroovyFile(idText, false, context);
    return (GrReferenceExpression) ((GroovyFileBase) file).getTopStatements()[0];
  }

  @Override
  public GrReferenceExpression createReferenceElementForClass(PsiClass aClass) {
    final String text;
    if (aClass instanceof PsiAnonymousClass) {
      text = ((PsiAnonymousClass)aClass).getBaseClassType().getPresentableText();
    }
    else {
      text = aClass.getName();
    }
    return createReferenceExpressionFromText(text);
  }

  @NotNull
  public GrExpression createExpressionFromText(@NotNull String text, PsiElement context) {
    GroovyFileImpl file = (GroovyFileImpl)createGroovyFile(text, false, context);
    GrTopStatement[] topStatements = file.getTopStatements();
    if (topStatements.length == 0 || !(topStatements[0] instanceof GrExpression)) {
      throw new IncorrectOperationException("incorrect expression = '" + text + "'");
    }
    return (GrExpression) topStatements[0];
  }

  public GrVariableDeclaration createVariableDeclaration(@Nullable String[] modifiers,
                                                         @Nullable GrExpression initializer,
                                                         @Nullable PsiType type,
                                                         String... identifiers) {
    StringBuilder text = writeModifiers(modifiers);

    if (type != null) {
      final PsiType unboxed = TypesUtil.unboxPrimitiveTypeWrapper(type);
      final String typeText = getTypeText(unboxed);
      text.append(typeText).append(" ");
    } else if (text.length() == 0) {
      text.insert(0, "def ");
    }

    if (identifiers.length > 1 && initializer != null) {
      text.append('(');
    }
    for (int i = 0; i < identifiers.length; i++) {
      if (i > 0) text.append(", ");
      String identifier = identifiers[i];
      text.append(identifier);
    }

    if (identifiers.length > 1 && initializer != null) {
      text.append(')');
    }

    if (initializer != null) {
      if (initializer instanceof GrApplicationStatement &&
          !GroovyConfigUtils.getInstance().isVersionAtLeast(initializer, GroovyConfigUtils.GROOVY1_8, false)) {
        initializer = createMethodCallByAppCall((GrApplicationStatement)initializer);
      }
      assert initializer != null;
      text.append(" = ").append(initializer.getText());
    }

    GrTopStatement[] topStatements = ((GroovyFileBase)createGroovyFile(text.toString())).getTopStatements();
    if (topStatements.length == 0 || !(topStatements[0] instanceof GrVariableDeclaration)) {
      topStatements = ((GroovyFileBase)createGroovyFile("def " + text)).getTopStatements();
    }
    if (topStatements.length == 0 || !(topStatements[0] instanceof GrVariableDeclaration)) {
      throw new RuntimeException("Invalid arguments, text = " + text);
    }

    final GrVariableDeclaration statement = (GrVariableDeclaration)topStatements[0];
    //todo switch-case formatting should work without this hack
    CodeEditUtil.markToReformatBefore(statement.getNode().findLeafElementAt(0), true);
    return statement;
  }

  @Override
  public GrEnumConstant createEnumConstantFromText(String text) {
    GroovyFile file = (GroovyFile)createGroovyFile("enum E{" + text + "}");
    final GrEnumTypeDefinition enumClass = (GrEnumTypeDefinition)file.getClasses()[0];
    return enumClass.getEnumConstants()[0];
  }

  public GrVariableDeclaration createFieldDeclaration(String[] modifiers,
                                                      String identifier,
                                                      @Nullable GrExpression initializer,
                                                      @Nullable PsiType type) {
    final String varDeclaration = createVariableDeclaration(modifiers, initializer, type, identifier).getText();

    final GroovyFileBase file = (GroovyFileBase) createGroovyFile("class A { " + varDeclaration + "}");
    final GrTypeDefinitionBody body = file.getTypeDefinitions()[0].getBody();
    LOG.assertTrue(body.getMemberDeclarations().length == 1 && body.getMemberDeclarations()[0] instanceof GrVariableDeclaration,
                   "ident = <" + identifier + "> initializer = " + (initializer == null ? "_null_" : ("<" + initializer.getText()) + ">"));
    return (GrVariableDeclaration) body.getMemberDeclarations()[0];
  }

  @Override
  public GrVariableDeclaration createFieldDeclarationFromText(String text) {
    final GroovyFile file = (GroovyFile)createGroovyFile("class X{\n" + text + "\n}");
    final PsiClass psiClass = file.getClasses()[0];
    return (GrVariableDeclaration)psiClass.getFields()[0].getParent();
  }

  private static StringBuilder writeModifiers(String[] modifiers) {
    StringBuilder text = new StringBuilder();
    if (!(modifiers == null || modifiers.length == 0)) {
      for (String modifier : modifiers) {
        text.append(modifier);
        text.append(" ");
      }
    }
    return text;
  }

  private static String getTypeText(PsiType type) {
    if (!(type instanceof PsiArrayType)) {
      final String canonical = type.getCanonicalText();
      return canonical != null ? canonical : type.getPresentableText();
    } else {
      return getTypeText(((PsiArrayType) type).getComponentType()) + "[]";
    }
  }

  @Nullable
  public GrTopStatement createTopElementFromText(String text) {
    PsiFile dummyFile = PsiFileFactory.getInstance(myProject).createFileFromText(DUMMY + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(), text);
    final GrTopStatement[] topStatements = ((GroovyFileBase)dummyFile).getTopStatements();
    LOG.assertTrue(topStatements.length == 1);
    return topStatements[0];
  }

  public GrClosableBlock createClosureFromText(String closureText, PsiElement context) throws IncorrectOperationException {
    GroovyFile psiFile = createGroovyFile("def foo  = " + closureText, false, context);
    final GrStatement st = psiFile.getStatements()[0];
    LOG.assertTrue(st instanceof GrVariableDeclaration, closureText);
    final GrExpression initializer = ((GrVariableDeclaration)st).getVariables()[0].getInitializerGroovy();
    LOG.assertTrue(initializer instanceof GrClosableBlock, closureText);
    return ((GrClosableBlock)initializer);
  }

  private GroovyFileImpl createDummyFile(String text, boolean physical) {
    final String fileName = DUMMY_FILE_NAME + '.' + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension();
    final long stamp = System.currentTimeMillis();
    final PsiFileFactory factory = PsiFileFactory.getInstance(myProject);
    return (GroovyFileImpl) factory.createFileFromText(fileName, GroovyFileType.GROOVY_FILE_TYPE, text, stamp, physical);
  }

  private GroovyFileImpl createDummyFile(String s) {
    return createDummyFile(s, false);
  }

  public GrParameter createParameter(String name, @Nullable String typeText, @Nullable String initializer, @Nullable GroovyPsiElement context)
    throws IncorrectOperationException {
    StringBuilder fileText = new StringBuilder();
    fileText.append("def foo(");
    if (typeText != null) {
      fileText.append(typeText).append(" ");
    } else {
      fileText.append("def ");
    }
    fileText.append(name);
    if (initializer != null && initializer.length() > 0) {
      fileText.append(" = ").append(initializer);
    }
    fileText.append("){}");
    GroovyFileImpl groovyFile = createDummyFile(fileText.toString());
    groovyFile.setContext(context);

    ASTNode node = groovyFile.getFirstChild().getNode();
    if (node.getElementType() != GroovyElementTypes.METHOD_DEFINITION) {
      throw new IncorrectOperationException("Invalid all text");
    }
    return ((GrMethod)node.getPsi()).getParameters()[0];
  }

  public GrCodeReferenceElement createTypeOrPackageReference(String qName) {
    final GroovyFileBase file = createDummyFile("def " + qName + " i");
    LOG.assertTrue(file.getTopStatements().length == 1 && (GrVariableDeclaration)file.getTopStatements()[0] instanceof GrVariableDeclaration, qName);
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

  @NotNull
  public GrTypeElement createTypeElement(String typeText, final PsiElement context) throws IncorrectOperationException {
    final GroovyFileBase file = createGroovyFile("def " + typeText + " someVar", false, context);

    GrTopStatement[] topStatements = file.getTopStatements();

    if (topStatements == null || topStatements.length == 0) throw new IncorrectOperationException("");
    GrTopStatement statement = topStatements[0];

    if (!(statement instanceof GrVariableDeclaration)) throw new IncorrectOperationException("");
    GrVariableDeclaration decl = (GrVariableDeclaration) statement;
    final GrTypeElement element = decl.getTypeElementGroovy();
    if (element == null) throw new IncorrectOperationException(typeText);

    return element;
  }

  public GrTypeElement createTypeElement(PsiType type) throws IncorrectOperationException {
    final String typeText = getTypeText(type);
    if (typeText == null)
      throw new IncorrectOperationException("Cannot create type element: cannot obtain text for type");
    return createTypeElement(typeText);
  }

  public GrParenthesizedExpression createParenthesizedExpr(GrExpression newExpr) {
    return ((GrParenthesizedExpression) getInstance(myProject).createExpressionFromText("(" + newExpr.getText() + ")"));
  }

  public PsiElement createStringLiteral(String text) {
    return ((GrReferenceExpression) createDummyFile("a.'" + text + "'").getTopStatements()[0]).getReferenceNameElement();
  }

  public PsiElement createModifierFromText(String name) {
    final GroovyFileBase file = createDummyFile(name + "\"foo\"() {}");
    return file.getTopLevelDefinitions()[0].getFirstChild().getFirstChild();
  }

  public GrCodeBlock createMethodBodyFromText(String text) {
    final GroovyFileBase file = createDummyFile("def foo () {" + text + "}");
    final GrMethod method = (GrMethod) file.getTopLevelDefinitions()[0];
    return method.getBlock();
  }

  public GrVariableDeclaration createSimpleVariableDeclaration(String name, String typeText) {
    String classText;
    if (Character.isLowerCase(typeText.charAt(0))) {
      classText = "class A { def " + typeText + " " + name + "}";
    } else {
      classText = "class A { " + typeText + " " + name + "}";
    }

    GroovyFileBase file = (GroovyFileBase) createGroovyFile(classText);
    final GrTypeDefinitionBody body = file.getTypeDefinitions()[0].getBody();
    return (GrVariableDeclaration) body.getMemberDeclarations()[0];
  }

  public GrReferenceElement createPackageReferenceElementFromText(String newPackageName) {
    return ((GrPackageDefinition) createDummyFile("package " + newPackageName).getTopStatements()[0]).getPackageReference();
  }

  public PsiElement createDotToken(String newDot) {
    return createReferenceExpressionFromText("a" + newDot + "b").getDotToken();
  }

  public GrMethod createConstructorFromText(@NotNull String constructorName,
                                                     @Nullable String[] paramTypes,
                                                     String[] paramNames,
                                                     String body,
                                                     PsiElement context) {
    final String text = generateMethodText(null, constructorName, null, paramTypes, paramNames, body, true);
    return createConstructorFromText(constructorName, text, context);
  }

  public GrMethod createConstructorFromText(String constructorName, String text, @Nullable PsiElement context) {
    GroovyFileImpl file = createDummyFile("class " + constructorName + "{" + text + "}");
    file.setContext(context);
    GrTopLevelDefinition definition = file.getTopLevelDefinitions()[0];
    assert definition != null && definition instanceof GrClassDefinition;
    return ((GrClassDefinition) definition).getGroovyMethods()[0];
  }

  @Override
  public GrLabel createLabel(@NotNull String name) {
    GroovyFileBase file = createDummyFile(name + ": println()");
    GrTopStatement definition = file.getTopStatements()[0];
    assert definition instanceof GrLabeledStatement;
    return ((GrLabeledStatement)definition).getLabel();
  }

  @NotNull
  public GrMethod createMethodFromText(@NotNull String methodText, @Nullable PsiElement context) {
    GroovyFileImpl file = createDummyFile(methodText);
    if (context != null) {
      file.setContext(context);
    }
    GrTopLevelDefinition[] definitions = file.getTopLevelDefinitions();
    if (definitions.length != 1) {
      throw new IncorrectOperationException("Can't create method from text: '" + file.getText() + "'");
    }
    GrTopLevelDefinition definition = definitions[0];
    if (!(definition instanceof GrMethod)) {
      throw new IncorrectOperationException("Can't create method from text: '" + file.getText() + "'");
    }
    return ((GrMethod)definition);
  }

  @NotNull
  @Override
  public GrAnnotation createAnnotationFromText(@NotNull @NonNls String annotationText, @Nullable PsiElement context) throws IncorrectOperationException {
    return createMethodFromText(annotationText + " void foo() {}", context).getModifierList().getAnnotations()[0];
  }

  @Override
  public GrMethod createMethodFromSignature(String name, GrClosureSignature signature) {
    StringBuilder builder = new StringBuilder("public");
    final PsiType returnType = signature.getReturnType();
    if (returnType != null) {
      builder.append(' ');
      builder.append(returnType.getCanonicalText());
    }

    builder.append(' ').append(name).append('(');
    int i = 0;
    for (GrClosureParameter parameter : signature.getParameters()) {
      final PsiType type = parameter.getType();
      if (type != null) {
        builder.append(type.getCanonicalText());
        builder.append(' ');
      }
      builder.append('p').append(++i);
      final GrExpression initializer = parameter.getDefaultInitializer();
      if (initializer != null) {
        builder.append(" = ").append(initializer.getText());
        builder.append(", ");
      }
    }
    if (signature.getParameterCount() > 0) {
      builder.delete(builder.length() - 2, builder.length());
    }

    builder.append("){}");
    return createMethodFromText(builder.toString());
  }

  @Override
  public GrAnnotation createAnnotationFromText(String annoText) {
    return createAnnotationFromText(annoText, null);
  }

  public GroovyFile createGroovyFile(String idText) {
    return createGroovyFile(idText, false, null);
  }

  public GroovyFile createGroovyFile(String idText, boolean isPhysical, @Nullable PsiElement context) {
    GroovyFileImpl file = createDummyFile(idText, isPhysical);
    file.setContext(context);
    return file;
  }

  public PsiElement createWhiteSpace() {
    PsiFile dummyFile = PsiFileFactory.getInstance(myProject).createFileFromText(DUMMY + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(),
        " ");
    return dummyFile.getFirstChild();
  }

  @NotNull
  public PsiElement createLineTerminator(int length) {

    String text = length <= 1 ? "\n" : "";
    if (length > 1) {
      text = StringUtil.repeatSymbol('\n', length);
    }

    PsiFile dummyFile = PsiFileFactory.getInstance(myProject).createFileFromText(DUMMY + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(),
        text);
    PsiElement child = dummyFile.getFirstChild();
    assert child != null;
    return child;
  }

  public GrArgumentList createExpressionArgumentList(GrExpression... expressions) {
    StringBuilder text = new StringBuilder();
    text.append("ven (");
    for (GrExpression expression : expressions) {
      text.append(expression.getText()).append(", ");
    }
    if (expressions.length > 0) {
      text.delete(text.length() - 2, text.length());
    }
    text.append(')');
    PsiFile file = createGroovyFile(text.toString());
    assert file.getChildren()[0] != null && (file.getChildren()[0] instanceof GrMethodCallExpression);
    return (((GrMethodCallExpression) file.getChildren()[0])).getArgumentList();
  }

  public GrNamedArgument createNamedArgument(@NotNull final String name, final GrExpression expression) {
    PsiFile file = createGroovyFile("foo (" + name + ":" + expression.getText() + ")");
    assert file.getChildren()[0] != null;
    GrCall call = (GrCall)file.getChildren()[0];
    return call.getArgumentList().getNamedArguments()[0];
  }

  public GrStatement createStatementFromText(String text) {
    return createStatementFromText(text, null);
  }

  @Override
  public GrStatement createStatementFromText(String text, @Nullable PsiElement context) {
    PsiFile file = createGroovyFile(text, false, context);
    assert ((GroovyFileBase) file).getTopStatements()[0] instanceof GrStatement;
    return (GrStatement) ((GroovyFileBase) file).getTopStatements()[0];
  }

  public GrBlockStatement createBlockStatement(@NonNls GrStatement... statements) {
    StringBuilder text = new StringBuilder();
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
    StringBuilder text = new StringBuilder();
    text.append(callExpr.getInvokedExpression().getText());
    text.append("(");
    final GrCommandArgumentList argumentList = callExpr.getArgumentList();
    if (argumentList != null) text.append(argumentList.getText());
    text.append(")");
    PsiFile file = createGroovyFile(text.toString());
    assert file.getChildren()[0] != null && (file.getChildren()[0] instanceof GrMethodCallExpression);
    return ((GrMethodCallExpression)file.getChildren()[0]);
  }

  @Override
  public GrCodeReferenceElement createCodeReferenceElementFromClass(PsiClass aClass) {
    if (aClass instanceof PsiAnonymousClass) {
      throw new IncorrectOperationException("cannot create code reference element for anonymous class " + aClass.getText());
    }

    return createCodeReferenceElementFromText(aClass.getQualifiedName());

  }

  @Override
  public GrCodeReferenceElement createCodeReferenceElementFromText(String text) {
    GroovyFile file = createGroovyFile("class X extends " + text + "{}");
    PsiClass[] classes = file.getClasses();
    if (classes.length != 1) throw new IncorrectOperationException("cannot create code reference element for class" + text);
    GrExtendsClause extendsClause = ((GrTypeDefinition)classes[0]).getExtendsClause();
    if (extendsClause == null) throw new IncorrectOperationException("cannot create code reference element for class" + text);
    GrCodeReferenceElement[] refElements = extendsClause.getReferenceElements();
    if (refElements.length != 1) throw new IncorrectOperationException("cannot create code reference element for class" + text);
    return refElements[0];
  }

  public GrImportStatement createImportStatementFromText(String qName, boolean isStatic, boolean isOnDemand, String alias) {
    final String text = "import " + (isStatic ? "static " : "") + qName + (isOnDemand ? ".*" : "") +
        (alias != null && alias.length() > 0 ? " as " + alias : "");

    PsiFile dummyFile = PsiFileFactory.getInstance(myProject).createFileFromText(DUMMY + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(),
        text);
    return ((GrImportStatement) dummyFile.getFirstChild());
  }

  public GrImportStatement createImportStatementFromText(@NotNull String text) {
    PsiFile dummyFile = PsiFileFactory.getInstance(myProject).createFileFromText(DUMMY + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension(),
        text);
    return ((GrImportStatement) dummyFile.getFirstChild());
  }


  private static String generateMethodText(@Nullable String modifier,
                                           String name,
                                           @Nullable String type,
                                           String[] paramTypes,
                                           String[] paramNames,
                                           @Nullable String body,
                                           boolean isConstructor) {
    StringBuilder builder = new StringBuilder();

    if (modifier != null){
      builder.append(modifier);
      builder.append(" ");
    }

    if (!isConstructor) {
      builder.append("def ");
    }

    //This is for constructor creation
    if (type != null) {
      builder.append(type);
      builder.append(" ");
    }

    builder.append(name);
    builder.append("(");

    for (int i = 0; i < paramNames.length; i++) {
      String paramType = paramTypes == null ? null : paramTypes[i];

      if (i > 0) builder.append(", ");

      if (paramType != null) {
        builder.append(paramType);
        builder.append(" ");
      }
      builder.append(paramNames[i]);
    }

    builder.append(")");
    if (body != null) {
      builder.append(body);
    } else {
      builder.append("{");
      builder.append("}");
    }

    return builder.toString();
  }

  public GrMethod createMethodFromText(String modifier, String name, @Nullable String type, String[] paramTypes, PsiElement context) {
    PsiType psiType;
    List<PsiType> res = new ArrayList<PsiType>();
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myProject);

    for (String paramType : paramTypes) {
      try {
        psiType = factory.createTypeElement(paramType).getType();
      }
      catch (IncorrectOperationException e) {
        psiType = PsiType.getJavaLangObject(PsiManager.getInstance(myProject), ProjectScope.getAllScope(myProject));
      }
      res.add(psiType);
    }

    String[] paramNames = QuickfixUtil.getMethodArgumentsNames(myProject, res.toArray(new PsiType[res.size()]));
    final String text = generateMethodText(modifier, name, type, paramTypes, paramNames, null, false);
    return createMethodFromText(text, context);
  }

  public GrDocComment createDocCommentFromText(String text) {
    return (GrDocComment)createGroovyFile(text).getFirstChild();
  }

  @Override
  public GrDocTag createDocTagFromText(String text) {
    final GrDocComment docComment = createDocCommentFromText("/**" + text + "*/");
    return docComment.getTags()[0];
  }

  @Override
  public GrConstructorInvocation createConstructorInvocation(String text) {
    return createConstructorInvocation(text, null);
  }

  @Override
  public GrConstructorInvocation createConstructorInvocation(String text, @Nullable PsiElement context) {
    GroovyFile file = createGroovyFile("class Foo{ def Foo(){" + text + "}}", false, context);
    return PsiImplUtil.getChainingConstructorInvocation((GrMethod)file.getClasses()[0].getConstructors()[0]);
  }

  @Override
  public PsiReferenceList createThrownList(PsiClassType[] exceptionTypes) {
    if (exceptionTypes.length == 0) {
      return createMethodFromText("def foo(){}", null).getThrowsList();
    }
    String[] types = new String[exceptionTypes.length];
    for (int i = 0; i < types.length; i++) {
      types[i] = exceptionTypes[i].getCanonicalText();
    }
    final String end = StringUtil.join(types, ",");
    return createMethodFromText("def foo() throws " + end + "{}", null).getThrowsList();
  }

  @Override
  public GrCatchClause createCatchClause(PsiClassType type, String parameterName) {
    StringBuilder buffer = new StringBuilder("try{} catch(");
    if (type == null) {
      buffer.append("Throwable ");
    }
    else {
      buffer.append(type.getCanonicalText()).append(" ");
    }
    buffer.append(parameterName).append("){\n}");
    final GrTryCatchStatement statement = (GrTryCatchStatement)createStatementFromText(buffer.toString());
    return statement.getCatchClauses()[0];
  }

  @Override
  public GrArgumentList createArgumentList() {
    return ((GrCall)createExpressionFromText("foo()")).getArgumentList();
  }

  public GrArgumentList createArgumentListFromText(String argListText) {
    try {
      return ((GrCall)createExpressionFromText("foo " + argListText)).getArgumentList();
    }
    catch (IncorrectOperationException e) {
      LOG.debug(argListText);
      throw e;
    }
  }

  @Override
  public GrExtendsClause createExtendsClause() {
    final GrTypeDefinition typeDefinition = createTypeDefinition("class A extends B {}");
    final GrExtendsClause clause = typeDefinition.getExtendsClause();
    clause.getReferenceElements()[0].delete();
    return clause;
  }

  @Override
  public GrImplementsClause createImplementsClause() {
    final GrTypeDefinition typeDefinition = createTypeDefinition("class A implements B {}");
    final GrImplementsClause clause = typeDefinition.getImplementsClause();
    clause.getReferenceElements()[0].delete();
    return clause;
  }

  @Override
  public GrLiteral createLiteralFromValue(@Nullable Object value) {
    if (value instanceof String) {
      return (GrLiteral)createStringLiteral((String)value);
    }

    if (value == null) {
      return (GrLiteral)createExpressionFromText("null");
    }

    if (value instanceof Boolean) {
      return (GrLiteral)createExpressionFromText(value.toString());
    }

    throw new IncorrectOperationException("Can not create literal from type: " + value.getClass().getName());
  }

  @NotNull
  @Override
  public PsiClass createClass(@NonNls @NotNull String name) throws IncorrectOperationException {
    return createTypeDefinition("class " + name + "{}");
  }

  @NotNull
  @Override
  public PsiClass createInterface(@NonNls @NotNull String name) throws IncorrectOperationException {
    return createTypeDefinition("interface " + name + "{}");
  }

  @NotNull
  @Override
  public PsiClass createEnum(@NotNull @NonNls String name) throws IncorrectOperationException {
    return createTypeDefinition("enum " + name + "{}");
  }

  @NotNull
  @Override
  public PsiField createField(@NotNull @NonNls String name, @NotNull PsiType type) throws IncorrectOperationException {
    final GrVariableDeclaration fieldDeclaration = createFieldDeclaration(new String[]{PsiModifier.PRIVATE}, name, null, type);
    return (PsiField)fieldDeclaration.getVariables()[0];
  }

  @NotNull
  @Override
  public GrMethod createMethod(@NotNull @NonNls String name, @Nullable PsiType returnType) throws IncorrectOperationException {
    StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      if (returnType != null) {
        builder.append(returnType.getCanonicalText());
      }
      else {
        builder.append("def");
      }
      builder.append(' ').append(name).append("(){}");
      return createMethodFromText(builder.toString());
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  @NotNull
  @Override
  public PsiMethod createConstructor() {
    return createConstructorFromText("Foo", "", null);
  }

  @NotNull
  @Override
  public PsiClassInitializer createClassInitializer() throws IncorrectOperationException {
    final GrTypeDefinition typeDefinition = createTypeDefinition("class X {{}}");
    return typeDefinition.getInitializers()[0];
  }

  @NotNull
  @Override
  public GrParameter createParameter(@NotNull @NonNls String name, @Nullable PsiType type) throws IncorrectOperationException {
    return createParameter(name, type == null ? null : type.getCanonicalText(), null, null);
  }

  @NotNull
  @Override
  public PsiParameterList createParameterList(@NotNull @NonNls String[] names, @NotNull PsiType[] types) throws IncorrectOperationException {
    StringBuilder builder = StringBuilderSpinAllocator.alloc();
    builder.append("def foo(");
    for (int i = 0; i < names.length; i++) {
      String name = names[i];
      final PsiType type = types[i];
      if (type != null) {
        builder.append(type.getCanonicalText());
        builder.append(' ');
      }
      builder.append(name);
      builder.append(',');
    }
    if (names.length > 0) {
      builder.delete(builder.length() - 1, builder.length());
    }
    builder.append("){}");
    final GrMethod method = createMethodFromText(builder.toString());
    return method.getParameterList();
  }

}
