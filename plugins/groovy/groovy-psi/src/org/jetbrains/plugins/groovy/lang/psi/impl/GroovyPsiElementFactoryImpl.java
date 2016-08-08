/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMemberReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTag;
import org.jetbrains.plugins.groovy.lang.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
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
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.ErrorUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author ven
 */
@SuppressWarnings("ConstantConditions")
public class GroovyPsiElementFactoryImpl extends GroovyPsiElementFactory {
  private static final Logger LOG = Logger.getInstance(GroovyPsiElementFactoryImpl.class);

  private final Project myProject;

  public GroovyPsiElementFactoryImpl(Project project) {
    myProject = project;
  }

  @Override
  @NotNull
  public PsiElement createReferenceNameFromText(String refName) {
    PsiFile file = createGroovyFileChecked("a." + refName);
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

  @Override
  public PsiElement createDocMemberReferenceNameFromText(String idText) {
    GrDocMemberReference reference = createDocMemberReferenceFromText("Foo", idText);
    LOG.assertTrue(reference != null, idText);
    return reference.getReferenceNameElement();
  }

  @Override
  public GrDocMemberReference createDocMemberReferenceFromText(String className, String text) {
    PsiFile file = createGroovyFileChecked("/** @see " + className + "#" + text + " */");
    PsiElement element = file.getFirstChild();
    assert element instanceof GrDocComment;
    GrDocTag tag = PsiTreeUtil.getChildOfType(element, GrDocTag.class);
    assert tag != null : "Doc tag points to null";
    return PsiTreeUtil.getChildOfType(tag, GrDocMemberReference.class);
  }

  @Override
  public GrDocReferenceElement createDocReferenceElementFromFQN(String qName) {
    PsiFile file = createGroovyFileChecked("/** @see " + qName + " */");
    PsiElement element = file.getFirstChild();
    assert element instanceof GrDocComment;
    GrDocTag tag = PsiTreeUtil.getChildOfType(element, GrDocTag.class);
    assert tag != null : "Doc tag points to null";
    return PsiTreeUtil.getChildOfType(tag, GrDocReferenceElement.class);
  }

  @Override
  public GrCodeReferenceElement createReferenceElementFromText(String refName, final PsiElement context) {
    GroovyFile file = createGroovyFileChecked("(" + refName + ")foo", false, context);
    GrTypeElement typeElement = ((GrTypeCastExpression) file.getTopStatements()[0]).getCastTypeElement();
    return ((GrClassTypeElement) typeElement).getReferenceElement();
  }

  @Override
  public GrReferenceExpression createReferenceExpressionFromText(String idText) {
    PsiFile file = createGroovyFileChecked(idText);
    final GrTopStatement[] statements = ((GroovyFileBase)file).getTopStatements();
    if (!(statements.length == 1 && statements[0] instanceof GrReferenceExpression)) throw new IncorrectOperationException(idText);
    return (GrReferenceExpression) statements[0];
  }

  @Override
  public GrReferenceExpression createReferenceExpressionFromText(String idText, PsiElement context) {
    GroovyFile file = createGroovyFileChecked(idText, false, context);
    GrTopStatement[] statements = file.getTopStatements();

    if (statements.length != 1) throw new IncorrectOperationException("refText: " + idText);
    if (!(statements[0] instanceof GrReferenceExpression)) throw new IncorrectOperationException("refText: " + idText);

    return (GrReferenceExpression)statements[0];
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

  @Override
  @NotNull
  public GrExpression createExpressionFromText(@NotNull String text, PsiElement context) {
    GroovyFile file = createGroovyFile(text, false, context);
    GrTopStatement[] topStatements = file.getTopStatements();
    if (topStatements.length == 0 || !(topStatements[0] instanceof GrExpression)) {
      throw new IncorrectOperationException("incorrect expression = '" + text + "'");
    }
    return (GrExpression) topStatements[0];
  }

  @NotNull
  @Override
  public GrCodeReferenceElement createReferenceElementByType(PsiClassType type) {
    if (type instanceof GrClassReferenceType) {
      GrReferenceElement reference = ((GrClassReferenceType)type).getReference();
      if (reference instanceof GrCodeReferenceElement) {
        return (GrCodeReferenceElement)reference;
      }
    }

    final PsiClassType.ClassResolveResult resolveResult = type.resolveGenerics();
    final PsiClass refClass = resolveResult.getElement();
    assert refClass != null : type;
    return createCodeReferenceElementFromText(type.getCanonicalText());
  }

  @NotNull
  @Override
  public PsiTypeParameterList createTypeParameterList() {
    return createMethodFromText("def <> void foo(){}").getTypeParameterList();
  }

  @NotNull
  @Override
  public PsiTypeParameter createTypeParameter(String name, PsiClassType[] superTypes) {
    StringBuilder builder = new StringBuilder();
    builder.append("def <").append(name);
    if (superTypes.length > 1 ||
        superTypes.length == 1 && !superTypes[0].equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
      builder.append(" extends ");
      for (PsiClassType type : superTypes) {
        if (type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) continue;
        builder.append(type.getCanonicalText()).append(',');
      }

      builder.delete(builder.length() - 1, builder.length());
    }
    builder.append("> void foo(){}");
    try {
      return createMethodFromText(builder).getTypeParameters()[0];
    }
    catch (RuntimeException e) {
      throw new IncorrectOperationException("type parameter text: " + builder);
    }
  }

  @Override
  public GrVariableDeclaration createVariableDeclaration(@Nullable String[] modifiers,
                                                         @Nullable GrExpression initializer,
                                                         @Nullable PsiType type,
                                                         String... identifiers) {

    String initializerText;
    if (initializer != null) {
      if (initializer instanceof GrApplicationStatement &&
          !GroovyConfigUtils.getInstance().isVersionAtLeast(initializer, GroovyConfigUtils.GROOVY1_8, false)) {
        initializer = createMethodCallByAppCall((GrApplicationStatement)initializer);
      }
      assert initializer != null;
      initializerText = initializer.getText();
    }
    else {
      initializerText = null;
    }

    return createVariableDeclaration(modifiers, initializerText, type, identifiers);
  }

  @Override
  public GrVariableDeclaration createVariableDeclaration(@Nullable String[] modifiers,
                                                         @Nullable String initializer,
                                                         @Nullable PsiType type,
                                                         String... identifiers) {
    StringBuilder text = writeModifiers(modifiers);

    if (type != null && type != PsiType.NULL) {
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

    if (!StringUtil.isEmptyOrSpaces(initializer)) {
      text.append(" = ").append(initializer);
    }

    GrTopStatement[] topStatements = createGroovyFileChecked(text).getTopStatements();
    if (topStatements.length == 0 || !(topStatements[0] instanceof GrVariableDeclaration)) {
      topStatements = createGroovyFileChecked("def " + text).getTopStatements();
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
    GroovyFile file = createGroovyFileChecked("enum E{" + text + "}");
    final GrEnumTypeDefinition enumClass = (GrEnumTypeDefinition)file.getClasses()[0];
    return enumClass.getEnumConstants()[0];
  }

  @Override
  public GrVariableDeclaration createFieldDeclaration(String[] modifiers,
                                                      String identifier,
                                                      @Nullable GrExpression initializer,
                                                      @Nullable PsiType type) {
    final String varDeclaration = createVariableDeclaration(modifiers, initializer, type, identifier).getText();

    final GroovyFileBase file = createGroovyFileChecked("class A { " + varDeclaration + "}");
    final GrTypeDefinitionBody body = file.getTypeDefinitions()[0].getBody();
    LOG.assertTrue(body.getMemberDeclarations().length == 1 && body.getMemberDeclarations()[0] instanceof GrVariableDeclaration,
                   "ident = <" + identifier + "> initializer = " + (initializer == null ? "_null_" : ("<" + initializer.getText()) + ">"));
    return (GrVariableDeclaration) body.getMemberDeclarations()[0];
  }

  @Override
  public GrVariableDeclaration createFieldDeclarationFromText(String text) {
    final GroovyFile file = createGroovyFileChecked("class X{\n" + text + "\n}");
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
      final String text = canonical != null ? canonical : type.getPresentableText();
      if ("null".equals(text)) {
        return "";
      }
      else {
        return text;
      }
    }
    else {
      return getTypeText(((PsiArrayType)type).getComponentType()) + "[]";
    }
  }

  @Override
  @Nullable
  public GrTopStatement createTopElementFromText(String text) {
    GroovyFile dummyFile = createGroovyFileChecked(text);
    final GrTopStatement[] topStatements = dummyFile.getTopStatements();
    if (topStatements.length != 1) throw new IncorrectOperationException("text = '" + text + "'");
    return topStatements[0];
  }

  @Override
  public GrClosableBlock createClosureFromText(String closureText, PsiElement context) throws IncorrectOperationException {
    GroovyFile psiFile = createGroovyFileChecked("def __hdsjfghk_sdhjfshglk_foo  = " + closureText, false, context);
    final GrStatement st = psiFile.getStatements()[0];
    LOG.assertTrue(st instanceof GrVariableDeclaration, closureText);
    final GrExpression initializer = ((GrVariableDeclaration)st).getVariables()[0].getInitializerGroovy();
    LOG.assertTrue(initializer instanceof GrClosableBlock, closureText);
    return ((GrClosableBlock)initializer);
  }

  private GroovyFileImpl createDummyFile(CharSequence text, boolean physical) {
    final String fileName = DUMMY_FILE_NAME + '.' + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension();
    final long stamp = System.currentTimeMillis();
    final PsiFileFactory factory = PsiFileFactory.getInstance(myProject);
    return (GroovyFileImpl) factory.createFileFromText(fileName, GroovyFileType.GROOVY_FILE_TYPE, text, stamp, physical);
  }

  @Override
  public GrParameter createParameter(String name,
                                     @Nullable String typeText,
                                     @Nullable String initializer,
                                     @Nullable GroovyPsiElement context,
                                     String... modifiers) throws IncorrectOperationException {
    try {
      StringBuilder fileText = new StringBuilder();
      fileText.append("def dsfsadfnbhfjks_weyripouh_huihnrecuio(");
      for (String modifier : modifiers) {
        fileText.append(modifier).append(' ');
      }
      if (StringUtil.isNotEmpty(typeText)) {
        fileText.append(typeText).append(' ');
      }
      fileText.append(name);
      if (initializer != null && !initializer.isEmpty()) {
        fileText.append(" = ").append(initializer);
      }
      fileText.append("){}");
      GroovyFile groovyFile = createGroovyFileChecked(fileText, false, context);

      ASTNode node = groovyFile.getFirstChild().getNode();
      return ((GrMethod)node.getPsi()).getParameters()[0];
    }
    catch (RuntimeException e) {
      throw new IncorrectOperationException("name = " + name + ", type = " + typeText + ", initializer = " + initializer);
    }
  }

  @Override
  public GrCodeReferenceElement createTypeOrPackageReference(String qName) {
    try {
      final GroovyFileBase file = createGroovyFileChecked("def i = new " + qName + "()");
      final GrStatement[] statements = file.getStatements();
      final GrVariableDeclaration variableDeclaration = (GrVariableDeclaration)statements[0];
      final GrVariable var = variableDeclaration.getVariables()[0];
      final GrExpression initializer = var.getInitializerGroovy();
      return ((GrNewExpression)initializer).getReferenceElement();
    }
    catch (RuntimeException e) {
      throw new IncorrectOperationException("reference text=" + qName, (Throwable)e);
    }
  }

  @Override
  public GrTypeDefinition createTypeDefinition(String text) throws IncorrectOperationException {
    final GroovyFileBase file = createGroovyFileChecked(text);
    final GrTypeDefinition[] classes = file.getTypeDefinitions();
    if (classes.length != 1) throw new IncorrectOperationException("Incorrect type definition text");
    return classes[0];
  }

  @Override
  @NotNull
  public GrTypeElement createTypeElement(String typeText, final PsiElement context) throws IncorrectOperationException {
    final GroovyFile file = createGroovyFileChecked("def " + typeText + " someVar", false, context);

    GrTopStatement[] topStatements = file.getTopStatements();

    if (topStatements == null || topStatements.length == 0) throw new IncorrectOperationException("can't create type element from:" + typeText);
    GrTopStatement statement = topStatements[0];

    if (!(statement instanceof GrVariableDeclaration)) throw new IncorrectOperationException("can't create type element from:" + typeText);
    GrVariableDeclaration decl = (GrVariableDeclaration) statement;
    final GrTypeElement element = decl.getTypeElementGroovy();
    if (element == null) throw new IncorrectOperationException("can't create type element from:" + typeText);

    return element;
  }

  @Override
  public GrTypeElement createTypeElement(PsiType type) throws IncorrectOperationException {
    final String typeText = getTypeText(type);
    if (typeText == null)
      throw new IncorrectOperationException("Cannot create type element: cannot obtain text for type");
    return createTypeElement(typeText);
  }

  @NotNull
  @Override
  public PsiClassType createType(@NotNull PsiClass aClass) {
    return JavaPsiFacade.getElementFactory(myProject).createType(aClass);
  }

  @Override
  public GrParenthesizedExpression createParenthesizedExpr(GrExpression expression) {
    return ((GrParenthesizedExpression) createExpressionFromText("(" + expression.getText() + ")"));
  }

  @Override
  public PsiElement createStringLiteralForReference(String text) {
    return createLiteralFromValue(text).getFirstChild();
  }

  @Override
  public PsiElement createModifierFromText(String name) {
    final GroovyFileBase file = createGroovyFileChecked(name + " foo() {}");
    final GrTopLevelDefinition[] definitions = file.getTopLevelDefinitions();
    if (definitions.length != 1) throw new IncorrectOperationException(name);
    return definitions[0].getFirstChild().getFirstChild();
  }

  @Override
  public GrCodeBlock createMethodBodyFromText(String text) {
    final GroovyFileBase file = createGroovyFileChecked("def foo () {" + text + "}");
    final GrMethod method = (GrMethod) file.getTopLevelDefinitions()[0];
    return method.getBlock();
  }

  @Override
  public GrVariableDeclaration createSimpleVariableDeclaration(String name, String typeText) {
    String classText;
    if (Character.isLowerCase(typeText.charAt(0))) {
      classText = "class A { def " + typeText + " " + name + "}";
    } else {
      classText = "class A { " + typeText + " " + name + "}";
    }

    GroovyFileBase file = createGroovyFileChecked(classText);
    final GrTypeDefinitionBody body = file.getTypeDefinitions()[0].getBody();
    return (GrVariableDeclaration) body.getMemberDeclarations()[0];
  }

  @Override
  public GrReferenceElement createPackageReferenceElementFromText(String newPackageName) {
    return ((GrPackageDefinition) createGroovyFileChecked("package " + newPackageName).getTopStatements()[0]).getPackageReference();
  }

  @Override
  public PsiElement createDotToken(String newDot) {
    return createReferenceExpressionFromText("a" + newDot + "b").getDotToken();
  }

  @Override
  public GrMethod createConstructorFromText(@NotNull String constructorName,
                                                     @Nullable String[] paramTypes,
                                                     String[] paramNames,
                                                     String body,
                                                     PsiElement context) {
    final CharSequence text = generateMethodText(null, constructorName, null, paramTypes, paramNames, body, true);
    return createConstructorFromText(constructorName, text, context);
  }

  @Override
  public GrMethod createConstructorFromText(String constructorName, CharSequence constructorText, @Nullable PsiElement context) {
    GroovyFile file = createGroovyFileChecked("class " + constructorName + "{" + constructorText + "}", false, context);
    GrTopLevelDefinition definition = file.getTopLevelDefinitions()[0];

    if (!( definition != null && definition instanceof GrClassDefinition)) {
      throw new IncorrectOperationException("constructorName: " + constructorName + ", text: " + constructorText);
    }
    GrMethod[] methods = ((GrClassDefinition)definition).getCodeMethods();
    if (methods.length != 1) {
      throw new IncorrectOperationException("constructorName: " + constructorName + ", text: " + constructorText);
    }
    return methods[0];
  }

  @Override
  @NotNull
  public GrMethod createMethodFromText(@NotNull String methodText, @Nullable PsiElement context) {
    GroovyFile file = createGroovyFile(methodText, false, context);

    GrTopStatement[] definitions = file.getTopStatements();
    if (definitions.length != 1) {
      throw new IncorrectOperationException("Can't create method from text: '" + file.getText() + "'");
    }
    GrTopStatement definition = definitions[0];
    if (!(definition instanceof GrMethod)) {
      throw new IncorrectOperationException("Can't create method from text: '" + file.getText() + "'");
    }
    return ((GrMethod)definition);
  }

  @NotNull
  @Override
  public GrAnnotation createAnnotationFromText(@NotNull @NonNls String annotationText, @Nullable PsiElement context) throws IncorrectOperationException {
    return createMethodFromText(annotationText + " void ___shdjklf_pqweirupncp_foo() {}", context).getModifierList().getRawAnnotations()[0];
  }

  @Override
  public GrMethod createMethodFromSignature(String name, GrClosureSignature signature) {
    StringBuilder builder = new StringBuilder("public");
    final PsiType returnType = signature.getReturnType();
    if (returnType != null && returnType != PsiType.NULL) {
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
    return createMethodFromText(builder);
  }

  @Override
  public GrAnnotation createAnnotationFromText(String annoText) {
    return createAnnotationFromText(annoText, null);
  }

  private GroovyFile createGroovyFileChecked(CharSequence idText) {
    return createGroovyFileChecked(idText, false, null);
  }

  private GroovyFile createGroovyFileChecked(@NotNull CharSequence idText, boolean isPhysical, @Nullable PsiElement context) {
    final GroovyFileImpl file = createDummyFile(idText, isPhysical);
    if (ErrorUtil.containsError(file)) {
      throw new IncorrectOperationException("cannot create file from text: " + idText);
    }
    file.setContext(context);
    return file;
  }

  /**
   * use createGroovyFileChecked() inside GroovyPsiElementFactoryImpl instead of this method
   */
  @Override
  public GroovyFile createGroovyFile(CharSequence idText, boolean isPhysical, @Nullable PsiElement context) {
    GroovyFileImpl file = createDummyFile(idText, isPhysical);
    file.setContext(context);
    return file;
  }

  @Override
  public PsiElement createWhiteSpace() {
    PsiFile dummyFile = createDummyFile(" ", false);
    return dummyFile.getFirstChild();
  }

  @Override
  @NotNull
  public PsiElement createLineTerminator(int length) {

    String text = length <= 1 ? "\n" : "";
    if (length > 1) {
      text = StringUtil.repeatSymbol('\n', length);
    }

    return createLineTerminator(text);
  }

  @Override
  @NotNull
  public PsiElement createLineTerminator(String text) {
    PsiFile dummyFile = createGroovyFileChecked(text);
    PsiElement child = dummyFile.getFirstChild();
    assert child != null;
    return child;
  }

  @Override
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
    PsiFile file = createGroovyFileChecked(text);
    assert file.getChildren()[0] != null && (file.getChildren()[0] instanceof GrMethodCallExpression);
    return (((GrMethodCallExpression) file.getChildren()[0])).getArgumentList();
  }

  @Override
  public GrNamedArgument createNamedArgument(@NotNull final String name, final GrExpression expression) {
    PsiFile file = createGroovyFileChecked("foo (" + name + ":" + expression.getText() + ")");
    assert file.getChildren()[0] != null;
    GrCall call = (GrCall)file.getChildren()[0];
    return call.getArgumentList().getNamedArguments()[0];
  }

  @Override
  public GrStatement createStatementFromText(CharSequence text) {
    return createStatementFromText(text, null);
  }

  @Override
  public GrStatement createStatementFromText(CharSequence text, @Nullable PsiElement context) {
    GroovyFile file = createGroovyFileChecked(text, false, context);
    GrTopStatement[] statements = file.getTopStatements();
    if (statements.length != 1) {
      throw new IncorrectOperationException("count = " + statements.length + ", " + text);
    }
    if (!(statements[0] instanceof GrStatement)) {
      throw new IncorrectOperationException("type = " + statements[0].getClass().getName() + ", " + text);
    }
    return (GrStatement)statements[0];
  }

  @Override
  public GrBlockStatement createBlockStatement(@NonNls GrStatement... statements) {
    StringBuilder text = new StringBuilder();
    text.append("while (true) { \n");
    for (GrStatement statement : statements) {
      text.append(statement.getText()).append("\n");
    }
    text.append("}");
    PsiFile file = createGroovyFileChecked(text);
    LOG.assertTrue(file.getChildren()[0] != null && (file.getChildren()[0] instanceof GrWhileStatement), text);
    return (GrBlockStatement) ((GrWhileStatement) file.getChildren()[0]).getBody();
  }

  @Override
  public GrMethodCallExpression createMethodCallByAppCall(GrApplicationStatement callExpr) {
    StringBuilder text = new StringBuilder();
    text.append(callExpr.getInvokedExpression().getText());
    text.append("(");
    final GrCommandArgumentList argumentList = callExpr.getArgumentList();
    if (argumentList != null) text.append(argumentList.getText());
    text.append(")");
    PsiFile file = createGroovyFileChecked(text);
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
    GroovyFile file = createGroovyFileChecked("class X extends " + text + "{}");
    PsiClass[] classes = file.getClasses();
    if (classes.length != 1) throw new IncorrectOperationException("cannot create code reference element for class" + text);
    GrExtendsClause extendsClause = ((GrTypeDefinition)classes[0]).getExtendsClause();
    if (extendsClause == null) throw new IncorrectOperationException("cannot create code reference element for class" + text);
    GrCodeReferenceElement[] refElements = extendsClause.getReferenceElementsGroovy();
    if (refElements.length != 1) throw new IncorrectOperationException("cannot create code reference element for class" + text);
    return refElements[0];
  }

  @Override
  public GrReferenceExpression createThisExpression(@Nullable PsiClass psiClass) {
    final String text;
    if (psiClass == null) {
      text = "this";
    }
    else {
      final String qname = psiClass.getQualifiedName();
      if (StringUtil.isEmpty(qname)) {
        text = "this";
      }
      else {
        text = qname + ".this";
      }
    }
    return createReferenceExpressionFromText(text, psiClass);
  }

  @Override
  public GrBlockStatement createBlockStatementFromText(String text, @Nullable PsiElement context) {
    GroovyFile file = createGroovyFileChecked("if(true)" + text, false, context);
    GrStatement[] statements = file.getStatements();
    LOG.assertTrue(statements.length == 1 && statements[0] instanceof GrIfStatement, text);

    GrStatement branch = ((GrIfStatement)statements[0]).getThenBranch();
    LOG.assertTrue(branch instanceof GrBlockStatement);
    return (GrBlockStatement)branch;
  }

  @Override
  public GrModifierList createModifierList(CharSequence text) {
    final GrMethod method = createMethodFromText(text + " void foo()");
    return method.getModifierList();
  }

  @Override
  public GrCaseSection createSwitchSection(String text) {
    final GrStatement statement = createStatementFromText("switch (a) {\n" + text + "\n}");
    if (!(statement instanceof GrSwitchStatement)) {
      throw new IncorrectOperationException("Cannot create switch section from text: " + text);
    }

    final GrCaseSection[] sections = ((GrSwitchStatement)statement).getCaseSections();
    if (sections.length != 1) throw new IncorrectOperationException("Cannot create switch section from text: " + text);
    return sections[0];
  }

  @Override
  public GrImportStatement createImportStatementFromText(@NotNull String qName, boolean isStatic, boolean isOnDemand, String alias) {
    return createImportStatement(qName, isStatic, isOnDemand, alias, null);
  }

  @Override
  public GrImportStatement createImportStatementFromText(@NotNull String text) {
    PsiFile dummyFile = createGroovyFileChecked(text);
    return ((GrImportStatement) dummyFile.getFirstChild());
  }

  @Override
  public GrImportStatement createImportStatement(@NotNull String qname,
                                                 boolean isStatic,
                                                 boolean isOnDemand,
                                                 String alias,
                                                 PsiElement context) {
    StringBuilder builder = new StringBuilder();
    builder.append("import ");
    if (isStatic) {
      builder.append("static ");
    }
    builder.append(qname);
    if (isOnDemand) {
      builder.append(".*");
    }
    if (StringUtil.isNotEmpty(alias)) {
      builder.append(" as ").append(alias);
    }

    PsiFile dummyFile = createGroovyFileChecked(builder, false, context);
    return ((GrImportStatement)dummyFile.getFirstChild());
  }


  private static CharSequence generateMethodText(@Nullable String modifier,
                                                 String name,
                                                 @Nullable String type,
                                                 String[] paramTypes,
                                                 String[] paramNames,
                                                 @Nullable String body,
                                                 boolean isConstructor) {
    StringBuilder builder = new StringBuilder();

    if (modifier != null) {
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
    }
    else {
      builder.append("{");
      builder.append("}");
    }

    return builder;
  }

  @Override
  public GrMethod createMethodFromText(String modifier, String name, @Nullable String type, String[] paramTypes, PsiElement context) {
    PsiType psiType;
    List<PsiType> res = new ArrayList<>();
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myProject);

    for (String paramType : paramTypes) {
      try {
        psiType = factory.createTypeElement(paramType).getType();
      }
      catch (IncorrectOperationException e) {
        psiType = TypesUtil.getJavaLangObject(context);
      }
      res.add(psiType);
    }

    String[] paramNames = GroovyNamesUtil.getMethodArgumentsNames(myProject, res.toArray(PsiType.createArray(res.size())));
    final CharSequence text = generateMethodText(modifier, name, type, paramTypes, paramNames, null, false);
    return createMethodFromText(text.toString(), context);
  }

  @Override
  @NotNull
  public GrDocComment createDocCommentFromText(@NotNull String text) {
    return (GrDocComment)createGroovyFileChecked(text).getFirstChild();
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
    GroovyFile file = createGroovyFileChecked("class Foo{ def Foo(){" + text + "}}", false, context);
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
    final GrTryCatchStatement statement = (GrTryCatchStatement)createStatementFromText(buffer);
    return statement.getCatchClauses()[0];
  }

  @Override
  public GrArgumentList createArgumentList() {
    return ((GrCall)createExpressionFromText("foo()")).getArgumentList();
  }

  @Override
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
    clause.getReferenceElementsGroovy()[0].delete();
    return clause;
  }

  @Override
  public GrImplementsClause createImplementsClause() {
    final GrTypeDefinition typeDefinition = createTypeDefinition("class A implements B {}");
    final GrImplementsClause clause = typeDefinition.getImplementsClause();
    clause.getReferenceElementsGroovy()[0].delete();
    return clause;
  }

  @Override
  public GrLiteral createLiteralFromValue(@Nullable Object value) {
    if (value instanceof String) {
      StringBuilder buffer = GrStringUtil.getLiteralTextByValue((String)value);
      final GrExpression expr = createExpressionFromText(buffer);
      LOG.assertTrue(expr instanceof GrLiteral, "value = " + value);
      return (GrLiteral)expr;
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
  public GrField createField(@NotNull @NonNls String name, @NotNull PsiType type) throws IncorrectOperationException {
    final GrVariableDeclaration fieldDeclaration = createFieldDeclaration(ArrayUtil.EMPTY_STRING_ARRAY, name, null, type);
    return (GrField)fieldDeclaration.getVariables()[0];
  }

  @Override
  public GrTraitTypeDefinition createTrait(String name) {
    return (GrTraitTypeDefinition)createTypeDefinition("trait " + name + "{}");
  }

  @NotNull
  @Override
  public GrMethod createMethod(@NotNull @NonNls String name, @Nullable PsiType returnType) throws IncorrectOperationException {
    return createMethod(name, returnType, null);
  }

  @NotNull
  @Override
  public GrMethod createMethod(@NotNull @NonNls String name, PsiType returnType, PsiElement context) throws IncorrectOperationException {
    StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("def <T>");
      if (returnType != null) {
        builder.append(returnType.getCanonicalText());
      }
      builder.append(' ');
      if (GroovyNamesUtil.isIdentifier(name)) {
        builder.append(name);
      }
      else {
        builder.append('"');
        builder.append(GrStringUtil.escapeSymbolsForGString(name, true, false));
        builder.append('"');
      }
      builder.append("(){}");
      GrMethod method = createMethodFromText(builder.toString(), context);
      if (returnType != null) {
        method.getModifierList().setModifierProperty(GrModifier.DEF, false);
      }
      PsiTypeParameterList typeParameterList = method.getTypeParameterList();
      assert typeParameterList != null;
      typeParameterList.getFirstChild().delete();
      typeParameterList.getFirstChild().delete();
      typeParameterList.getFirstChild().delete();
      return method;
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  @NotNull
  @Override
  public GrMethod createConstructor() {
    return createConstructorFromText("Foo", "Foo(){}", null);
  }

  @NotNull
  @Override
  public GrClassInitializer createClassInitializer() throws IncorrectOperationException {
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
  public GrParameter createParameter(@NotNull @NonNls String name, @Nullable PsiType type, PsiElement context) throws IncorrectOperationException {
    return createParameter(name, type == null ? null : type.getCanonicalText(), null, context instanceof GroovyPsiElement ? (GroovyPsiElement)context : null);
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
    final GrMethod method = createMethodFromText(builder);
    return method.getParameterList();
  }

  @NotNull
  @Override
  public PsiClass createAnnotationType(@NotNull @NonNls String name) throws IncorrectOperationException {
    return createTypeDefinition("@interface " + name + "{}");
  }

  @NotNull
  @Override
  public PsiMethod createConstructor(@NotNull @NonNls String name) {
    return createConstructorFromText(name, name + "(){}", null);
  }

  @Override
  public PsiMethod createConstructor(@NotNull @NonNls String name, PsiElement context) {
    return createConstructorFromText(name, name + "(){}", context);
  }

  @NotNull
  @Override
  public PsiClassType createType(@NotNull PsiClass resolve, @NotNull PsiSubstitutor substitutor) {
    return JavaPsiFacade.getElementFactory(myProject).createType(resolve, substitutor);
  }

  @NotNull
  @Override
  public PsiClassType createType(@NotNull PsiClass resolve, @NotNull PsiSubstitutor substitutor, @NotNull LanguageLevel languageLevel) {
    return JavaPsiFacade.getElementFactory(myProject).createType(resolve, substitutor, languageLevel);
  }

  @SuppressWarnings("deprecation")
  @NotNull
  @Override
  public PsiClassType createType(@NotNull PsiClass resolve,
                                 @NotNull PsiSubstitutor substitutor,
                                 @NotNull LanguageLevel languageLevel,
                                 @NotNull PsiAnnotation[] annotations) {
    return JavaPsiFacade.getElementFactory(myProject).createType(resolve, substitutor, languageLevel, annotations);
  }

  @NotNull
  @Override
  public PsiClassType createType(@NotNull PsiClass aClass, PsiType parameters) {
    return JavaPsiFacade.getElementFactory(myProject).createType(aClass, parameters);
  }

  @NotNull
  @Override
  public PsiClassType createType(@NotNull PsiClass aClass, PsiType... parameters) {
    return JavaPsiFacade.getElementFactory(myProject).createType(aClass, parameters);
  }

  @NotNull
  @Override
  public PsiSubstitutor createRawSubstitutor(@NotNull PsiTypeParameterListOwner owner) {
    return JavaPsiFacade.getElementFactory(myProject).createRawSubstitutor(owner);
  }

  @NotNull
  @Override
  public PsiSubstitutor createSubstitutor(@NotNull Map<PsiTypeParameter, PsiType> map) {
    return JavaPsiFacade.getElementFactory(myProject).createSubstitutor(map);
  }

  @Override
  public PsiPrimitiveType createPrimitiveType(@NotNull String text) {
    return JavaPsiFacade.getElementFactory(myProject).createPrimitiveType(text);
  }

  @NotNull
  @Override
  public PsiClassType createTypeByFQClassName(@NotNull @NonNls String qName) {
    return JavaPsiFacade.getElementFactory(myProject).createTypeByFQClassName(qName);
  }

  @NotNull
  @Override
  public PsiClassType createTypeByFQClassName(@NotNull @NonNls String qName, @NotNull GlobalSearchScope resolveScope) {
    return JavaPsiFacade.getElementFactory(myProject).createTypeByFQClassName(qName, resolveScope);
  }

  @Override
  public boolean isValidClassName(@NotNull String name) {
    return GroovyNamesUtil.isIdentifier(name);
  }

  @Override
  public boolean isValidMethodName(@NotNull String name) {
    return true;
  }

  @Override
  public boolean isValidParameterName(@NotNull String name) {
    return GroovyNamesUtil.isIdentifier(name);
  }

  @Override
  public boolean isValidFieldName(@NotNull String name) {
    return GroovyNamesUtil.isIdentifier(name);
  }

  @Override
  public boolean isValidLocalVariableName(@NotNull String name) {
    return GroovyNamesUtil.isIdentifier(name);
  }
}
