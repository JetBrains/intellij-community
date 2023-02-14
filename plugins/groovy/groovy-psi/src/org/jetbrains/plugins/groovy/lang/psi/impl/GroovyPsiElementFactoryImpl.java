// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.GeneratedMarkerVisitor;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocComment;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocMemberReference;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocReferenceElement;
import org.jetbrains.plugins.groovy.lang.groovydoc.psi.api.GrDocTag;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GrLambdaExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.ErrorUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*;

@SuppressWarnings("ConstantConditions")
public class GroovyPsiElementFactoryImpl extends GroovyPsiElementFactory {
  private static final Logger LOG = Logger.getInstance(GroovyPsiElementFactoryImpl.class);

  private final Project myProject;
  private final PsiManager myManager;

  public GroovyPsiElementFactoryImpl(Project project) {
    myProject = project;
    myManager = PsiManager.getInstance(project);
  }

  @Override
  @NotNull
  public PsiElement createReferenceNameFromText(@NotNull String refName) {
    GroovyFileBase file = createGroovyFileChecked("a." + refName);
    GrTopStatement statement = file.getTopStatements()[0];
    if (!(statement instanceof GrReferenceExpression)) {
      throw new IncorrectOperationException("Incorrect reference name: " + refName);
    }
    final PsiElement element = ((GrReferenceExpression)statement).getReferenceNameElement();
    if (element == null) {
      throw new IncorrectOperationException("Incorrect reference name: " + refName);
    }
    return element;
  }

  @NotNull
  @Override
  public PsiElement createDocMemberReferenceNameFromText(@NotNull String idText) {
    GrDocMemberReference reference = createDocMemberReferenceFromText("Foo", idText);
    LOG.assertTrue(reference != null, idText);
    return reference.getReferenceNameElement();
  }

  @NotNull
  @Override
  public GrDocMemberReference createDocMemberReferenceFromText(@NotNull String className, @NotNull String text) {
    PsiFile file = createGroovyFileChecked("/** @see " + className + "#" + text + " */");
    PsiElement element = file.getFirstChild();
    assert element instanceof GrDocComment;
    GrDocTag tag = PsiTreeUtil.getChildOfType(element, GrDocTag.class);
    assert tag != null : "Doc tag points to null";
    return PsiTreeUtil.getChildOfType(tag, GrDocMemberReference.class);
  }

  @NotNull
  @Override
  public GrDocReferenceElement createDocReferenceElementFromFQN(@NotNull String qName) {
    PsiFile file = createGroovyFileChecked("/** @see " + qName + " */");
    PsiElement element = file.getFirstChild();
    assert element instanceof GrDocComment;
    GrDocTag tag = PsiTreeUtil.getChildOfType(element, GrDocTag.class);
    assert tag != null : "Doc tag points to null";
    return PsiTreeUtil.getChildOfType(tag, GrDocReferenceElement.class);
  }

  @NotNull
  @Override
  public GrCodeReferenceElement createCodeReference(@NotNull String text, @Nullable PsiElement context) {
    return createElementFromText(text, context, CODE_REFERENCE, GrCodeReferenceElement.class);
  }

  @NotNull
  @Override
  public GrReferenceExpression createReferenceExpressionFromText(@NotNull String idText) {
    GroovyFileBase file = createGroovyFileChecked(idText);
    final GrTopStatement[] statements = file.getTopStatements();
    if (!(statements.length == 1 && statements[0] instanceof GrReferenceExpression)) throw new IncorrectOperationException(idText);
    return (GrReferenceExpression) statements[0];
  }

  @NotNull
  @Override
  public GrReferenceExpression createReferenceExpressionFromText(@NotNull String idText, PsiElement context) {
    GroovyFile file = createGroovyFileChecked(idText, false, context);
    GrTopStatement[] statements = file.getTopStatements();

    if (statements.length != 1) throw new IncorrectOperationException("refText: " + idText);
    if (!(statements[0] instanceof GrReferenceExpression)) throw new IncorrectOperationException("refText: " + idText);

    return (GrReferenceExpression)statements[0];
  }

  @NotNull
  @Override
  public GrReferenceExpression createReferenceElementForClass(@NotNull PsiClass aClass) {
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
      return ((GrClassReferenceType)type).getReference();
    }

    final PsiClassType.ClassResolveResult resolveResult = type.resolveGenerics();
    final PsiClass refClass = resolveResult.getElement();
    assert refClass != null : type;
    return createCodeReference(type.getCanonicalText());
  }

  @NotNull
  @Override
  public PsiTypeParameterList createTypeParameterList() {
    return createMethodFromText("def <> void foo(){}").getTypeParameterList();
  }

  @NotNull
  @Override
  public PsiTypeParameter createTypeParameter(@NotNull String name, PsiClassType @NotNull [] superTypes) {
    @NlsSafe StringBuilder builder = new StringBuilder();
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

  @NotNull
  @Override
  public GrVariableDeclaration createVariableDeclaration(String @Nullable [] modifiers,
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

  @NotNull
  @Override
  public GrVariableDeclaration createVariableDeclaration(String @Nullable [] modifiers,
                                                         @Nullable String initializer,
                                                         @Nullable PsiType type,
                                                         String... identifiers) {
    @NlsSafe StringBuilder text = writeModifiers(modifiers);

    if (type != null && type != PsiTypes.nullType()) {
      final PsiType unboxed = TypesUtil.unboxPrimitiveTypeWrapper(type);
      final String typeText = getTypeText(unboxed);
      text.append(typeText).append(" ");
    } else if (text.length() == 0) {
      text.insert(0, "def ");
    }

    if (identifiers.length > 1 && initializer != null) {
      text.append('(');
    }

    text.append(String.join(", ", identifiers));

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
    if (topStatements.length == 0 || !(topStatements[0] instanceof GrVariableDeclaration statement)) {
      throw new RuntimeException("Invalid arguments, text = " + text);
    }

    //todo switch-case formatting should work without this hack
    CodeEditUtil.markToReformatBefore(statement.getNode().findLeafElementAt(0), true);
    return statement;
  }

  @NotNull
  @Override
  public GrEnumConstant createEnumConstantFromText(@NotNull String text) {
    GroovyFile file = createGroovyFileChecked("enum E{" + text + "}");
    final GrEnumTypeDefinition enumClass = (GrEnumTypeDefinition)file.getClasses()[0];
    return enumClass.getEnumConstants()[0];
  }

  @NotNull
  @Override
  public GrVariableDeclaration createFieldDeclaration(String @NotNull [] modifiers,
                                                      @NotNull String identifier,
                                                      @Nullable GrExpression initializer,
                                                      @Nullable PsiType type) {
    final String varDeclaration = createVariableDeclaration(modifiers, initializer, type, identifier).getText();

    final GroovyFileBase file = createGroovyFileChecked("class A { " + varDeclaration + "}");
    final GrTypeDefinitionBody body = file.getTypeDefinitions()[0].getBody();
    LOG.assertTrue(body.getMemberDeclarations().length == 1 && body.getMemberDeclarations()[0] instanceof GrVariableDeclaration,
                   "ident = <" + identifier + "> initializer = " + (initializer == null ? "_null_" : ("<" + initializer.getText()) + ">"));
    return (GrVariableDeclaration) body.getMemberDeclarations()[0];
  }

  @NotNull
  @Override
  public GrVariableDeclaration createFieldDeclarationFromText(@NotNull String text) {
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
      if (PsiKeyword.NULL.equals(text)) {
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

  @NotNull
  @Override
  public GrTopStatement createTopElementFromText(@NotNull String text) {
    GroovyFile dummyFile = createGroovyFileChecked(text);
    final GrTopStatement[] topStatements = dummyFile.getTopStatements();
    if (topStatements.length != 1) throw new IncorrectOperationException("text = '" + text + "'");
    return topStatements[0];
  }

  @NotNull
  @Override
  public GrClosableBlock createClosureFromText(@NotNull String closureText, PsiElement context) throws IncorrectOperationException {
    return createElementFromText(closureText, context, CLOSURE, GrClosableBlock.class);
  }

  @NotNull
  @Override
  public GrLambdaExpression createLambdaFromText(@NotNull String lambdaText, PsiElement context) throws IncorrectOperationException {
    return createElementFromText(lambdaText, context, LAMBDA_EXPRESSION, GrLambdaExpression.class);
  }

  private GroovyFileImpl createDummyFile(@NotNull CharSequence text, boolean physical) {
    final String fileName = DUMMY_FILE_NAME + '.' + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension();
    final long stamp = System.currentTimeMillis();
    final PsiFileFactory factory = PsiFileFactory.getInstance(myProject);
    return (GroovyFileImpl) factory.createFileFromText(fileName, GroovyFileType.GROOVY_FILE_TYPE, text, stamp, physical);
  }

  @NotNull
  @Override
  public GrParameter createParameter(@NotNull String name,
                                     @Nullable String typeText,
                                     @Nullable String initializer,
                                     @Nullable GroovyPsiElement context,
                                     String... modifiers) throws IncorrectOperationException {
    try {
      @NonNls StringBuilder fileText = new StringBuilder();
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

  @NotNull
  @Override
  public GrTypeDefinition createTypeDefinition(@NotNull String text) throws IncorrectOperationException {
    final GroovyFileBase file = createGroovyFileChecked(text);
    final GrTypeDefinition[] classes = file.getTypeDefinitions();
    if (classes.length != 1) throw new IncorrectOperationException("Incorrect type definition text");
    return classes[0];
  }

  @Override
  @NotNull
  public GrTypeElement createTypeElement(@NotNull String typeText, @Nullable final PsiElement context) throws IncorrectOperationException {
    return createElementFromText(typeText, context, TYPE_ELEMENT, GrTypeElement.class);
  }

  @NotNull
  @Override
  public GrTypeElement createTypeElement(@NotNull PsiType type) throws IncorrectOperationException {
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

  @NotNull
  @Override
  public GrParenthesizedExpression createParenthesizedExpr(@NotNull GrExpression expression, @Nullable PsiElement context) {
    return ((GrParenthesizedExpression)createExpressionFromText("(" + expression.getText() + ")", context));
  }

  @NotNull
  @Override
  public PsiElement createStringLiteralForReference(@NotNull String text) {
    return createLiteralFromValue(text).getFirstChild();
  }

  @NotNull
  @Override
  public PsiElement createModifierFromText(@NotNull String name) {
    final GroovyFileBase file = createGroovyFileChecked(name + " foo() {}");
    final GrTopStatement[] definitions = file.getTopStatements();
    if (definitions.length != 1) throw new IncorrectOperationException(name);
    return definitions[0].getFirstChild().getFirstChild();
  }

  @NotNull
  @Override
  public GrCodeBlock createMethodBodyFromText(@NotNull String text) {
    final GroovyFileBase file = createGroovyFileChecked("def foo () {" + text + "}");
    final GrMethod method = (GrMethod) file.getTopStatements()[0];
    return method.getBlock();
  }

  @NotNull
  @Override
  public GrVariableDeclaration createSimpleVariableDeclaration(@NotNull String name, @NotNull String typeText) {
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

  @NotNull
  @Override
  public PsiElement createDotToken(@NotNull String newDot) {
    return createReferenceExpressionFromText("a" + newDot + "b").getDotToken();
  }

  @NotNull
  @Override
  public GrMethod createConstructorFromText(@NotNull String constructorName,
                                            String @Nullable [] paramTypes,
                                            String @NotNull [] paramNames,
                                            @Nullable String body,
                                            @Nullable PsiElement context) {
    final CharSequence text = generateMethodText(null, constructorName, null, paramTypes, paramNames, body, true);
    return createConstructorFromText(constructorName, text, context);
  }

  @NotNull
  @Override
  public GrMethod createConstructorFromText(String constructorName, CharSequence constructorText, @Nullable PsiElement context) {
    GroovyFile file = createGroovyFileChecked("class " + constructorName + "{" + constructorText + "}", false, context);
    GrTypeDefinition definition = file.getTypeDefinitions()[0];

    if (definition == null) {
      throw new IncorrectOperationException("constructorName: " + constructorName + ", text: " + constructorText);
    }

    GrMethod[] methods = definition.getCodeMethods();
    if (methods.length != 1) {
      throw new IncorrectOperationException("constructorName: " + constructorName + ", text: " + constructorText);
    }

    return methods[0];
  }

  @Override
  @NotNull
  public GrMethod createMethodFromText(String methodText, @Nullable PsiElement context) {
    if (methodText == null) throw new IncorrectOperationException("Method text not provided");
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
  public GrAnnotation createAnnotationFromText(@NotNull @NonNls String annotationText, @Nullable PsiElement context) {
    return createElementFromText(annotationText, context, ANNOTATION, GrAnnotation.class);
  }

  @NotNull
  @Override
  public GrAnnotationNameValuePair createAnnotationAttribute(@NotNull String text, @Nullable PsiElement context) {
    return createElementFromText(text, context, ANNOTATION_MEMBER_VALUE_PAIR, GrAnnotationNameValuePair.class);
  }

  @NotNull
  @Override
  public GrMethod createMethodFromSignature(@NotNull String name, @NotNull GrSignature signature) {
    StringBuilder builder = new StringBuilder(PsiKeyword.PUBLIC);
    final PsiType returnType = signature.getReturnType();
    if (returnType != null && returnType != PsiTypes.nullType()) {
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

  @NotNull
  @Override
  public GrAnnotation createAnnotationFromText(@NotNull String annoText) {
    return createAnnotationFromText(annoText, null);
  }

  private GroovyFile createGroovyFileChecked(@NlsSafe @NotNull CharSequence idText) {
    return createGroovyFileChecked(idText, false, null);
  }

  private GroovyFile createGroovyFileChecked(@NlsSafe @NotNull CharSequence idText, boolean isPhysical, @Nullable PsiElement context) {
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
  @NotNull
  @Override
  public GroovyFile createGroovyFile(@NotNull CharSequence idText, boolean isPhysical, @Nullable PsiElement context) {
    GroovyFileImpl file = createDummyFile(idText, isPhysical);
    file.setContext(context);
    return file;
  }

  @NotNull
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
  public PsiElement createLineTerminator(@NotNull String text) {
    PsiFile dummyFile = createGroovyFileChecked(text);
    PsiElement child = dummyFile.getFirstChild();
    assert child != null;
    return child;
  }

  @NotNull
  @Override
  public GrArgumentList createExpressionArgumentList(GrExpression... expressions) {
    @NonNls StringBuilder text = new StringBuilder();
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

  @NotNull
  @Override
  public GrNamedArgument createNamedArgument(@NotNull final String name, @NotNull final GrExpression expression) {
    PsiFile file = createGroovyFileChecked("foo (" + name + ":" + expression.getText() + ")");
    assert file.getChildren()[0] != null;
    GrCall call = (GrCall)file.getChildren()[0];
    return call.getArgumentList().getNamedArguments()[0];
  }

  @NotNull
  @Override
  public GrStatement createStatementFromText(@NotNull CharSequence text) {
    return createStatementFromText(text, null);
  }

  @NotNull
  @Override
  public GrStatement createStatementFromText(@NotNull CharSequence text, @Nullable PsiElement context) {
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

  @NotNull
  @Override
  public GrMethodCallExpression createMethodCallByAppCall(@NotNull GrApplicationStatement callExpr) {
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

  @NotNull
  @Override
  public GrCodeReferenceElement createCodeReferenceElementFromClass(@NotNull PsiClass aClass) {
    String qualifiedName = aClass.getQualifiedName();
    if (qualifiedName == null) {
      throw new IncorrectOperationException("cannot create code reference element for class " + aClass.getText());
    }
    return createCodeReference(qualifiedName);
  }

  @NotNull
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

  @NotNull
  @Override
  public GrBlockStatement createBlockStatementFromText(@NotNull String text, @Nullable PsiElement context) {
    return createElementFromText(text, context, BLOCK_STATEMENT, GrBlockStatement.class);
  }

  @NotNull
  @Override
  public GrModifierList createModifierList(@NotNull CharSequence text) {
    final GrMethod method = createMethodFromText(text + " void foo()");
    return method.getModifierList();
  }

  @NotNull
  @Override
  public GrCaseSection createSwitchSection(@NotNull String text) {
    final GrStatement statement = createStatementFromText("switch (a) {\n" + text + "\n}");
    if (!(statement instanceof GrSwitchElement)) {
      throw new IncorrectOperationException("Cannot create switch section from text: " + text);
    }

    final GrCaseSection[] sections = ((GrSwitchElement)statement).getCaseSections();
    if (sections.length != 1) throw new IncorrectOperationException("Cannot create switch section from text: " + text);
    return sections[0];
  }

  @NotNull
  @Override
  public GrImportStatement createImportStatementFromText(@NotNull String qName, boolean isStatic, boolean isOnDemand, String alias) {
    return createImportStatement(qName, isStatic, isOnDemand, alias, null);
  }

  @NotNull
  @Override
  public GrImportStatement createImportStatementFromText(@NotNull String text) {
    PsiFile dummyFile = createGroovyFileChecked(text);
    return ((GrImportStatement) dummyFile.getFirstChild());
  }

  @NotNull
  @Override
  public GrImportStatement createImportStatement(@NotNull String qname,
                                                 boolean isStatic,
                                                 boolean isOnDemand,
                                                 String alias,
                                                 PsiElement context) {
    @NlsSafe StringBuilder builder = new StringBuilder();
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


  @NlsSafe
  private static CharSequence generateMethodText(@Nullable String modifier,
                                                 @NotNull String name,
                                                 @Nullable String type,
                                                 String @NotNull [] paramTypes,
                                                 String @NotNull [] paramNames,
                                                 @Nullable String body,
                                                 boolean isConstructor) {
    @NlsSafe StringBuilder builder = new StringBuilder();

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

  @NotNull
  @Override
  public GrMethod createMethodFromText(@NotNull String modifier, @NotNull String name, @Nullable String type, String @NotNull [] paramTypes, PsiElement context) {
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

  @NotNull
  @Override
  public GrConstructorInvocation createConstructorInvocation(@NotNull String text) {
    return createConstructorInvocation(text, null);
  }

  @NotNull
  @Override
  public GrConstructorInvocation createConstructorInvocation(@NotNull String text, @Nullable PsiElement context) {
    GroovyFile file = createGroovyFileChecked("class Foo{ def Foo(){" + text + "}}", false, context);
    return PsiImplUtil.getChainingConstructorInvocation((GrMethod)file.getClasses()[0].getConstructors()[0]);
  }

  @NotNull
  @Override
  public PsiReferenceList createThrownList(PsiClassType @NotNull [] exceptionTypes) {
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

  @NotNull
  @Override
  public GrCatchClause createCatchClause(@NotNull PsiClassType type, @NotNull String parameterName) {
    @NonNls StringBuilder buffer = new StringBuilder("try{} catch(");
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

  @NotNull
  @Override
  public GrArgumentList createArgumentList() {
    return ((GrCall)createExpressionFromText("foo()")).getArgumentList();
  }

  @NotNull
  @Override
  public GrArgumentList createArgumentListFromText(@NotNull String argListText) {
    try {
      return ((GrCall)createExpressionFromText("foo " + argListText)).getArgumentList();
    }
    catch (IncorrectOperationException e) {
      LOG.debug(argListText);
      throw e;
    }
  }

  @NotNull
  @Override
  public GrExtendsClause createExtendsClause() {
    final GrTypeDefinition typeDefinition = createTypeDefinition("class A extends B {}");
    final GrExtendsClause clause = typeDefinition.getExtendsClause();
    clause.getReferenceElementsGroovy()[0].delete();
    return clause;
  }

  @NotNull
  @Override
  public GrImplementsClause createImplementsClause() {
    final GrTypeDefinition typeDefinition = createTypeDefinition("class A implements B {}");
    final GrImplementsClause clause = typeDefinition.getImplementsClause();
    clause.getReferenceElementsGroovy()[0].delete();
    return clause;
  }

  @NotNull
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
    final GrVariableDeclaration fieldDeclaration = createFieldDeclaration(ArrayUtilRt.EMPTY_STRING_ARRAY, name, null, type);
    return (GrField)fieldDeclaration.getVariables()[0];
  }

  @NotNull
  @Override
  public GrTraitTypeDefinition createTrait(@NotNull String name) {
    return (GrTraitTypeDefinition)createTypeDefinition("trait " + name + "{}");
  }

  @NotNull
  @Override
  public GrTraitTypeDefinition createRecord(@NotNull String name) {
    return (GrTraitTypeDefinition)createTypeDefinition("record " + name + "() {}");
  }

  @NotNull
  @Override
  public GrMethod createMethod(@NotNull @NonNls String name, @Nullable PsiType returnType) throws IncorrectOperationException {
    return createMethod(name, returnType, null);
  }

  @NotNull
  @Override
  public GrMethod createMethod(@NotNull @NonNls String name, PsiType returnType, PsiElement context) throws IncorrectOperationException {
    @NonNls final StringBuilder builder = new StringBuilder();
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
    PsiTypeParameterList typeParameterList = method.getTypeParameterList();
    assert typeParameterList != null;
    typeParameterList.getFirstChild().delete();
    typeParameterList.getFirstChild().delete();
    typeParameterList.getFirstChild().delete();

    if (returnType != null) {
      method.getModifierList().setModifierProperty(GrModifier.DEF, false);
    }

    return method;
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
  public PsiParameterList createParameterList(@NonNls String @NotNull [] names, PsiType @NotNull [] types) throws IncorrectOperationException {
    @NonNls final StringBuilder builder = new StringBuilder();
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

  @NotNull
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
  public PsiClassType createType(@NotNull PsiClass resolve, @NotNull PsiSubstitutor substitutor, @Nullable LanguageLevel languageLevel) {
    return JavaPsiFacade.getElementFactory(myProject).createType(resolve, substitutor, languageLevel);
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

  @NotNull
  private <T extends PsiElement> T createElementFromText(@NotNull String text,
                                                         @Nullable PsiElement context,
                                                         @NotNull IElementType elementType,
                                                         @NotNull Class<T> elementClass) {
    final GroovyDummyElement dummyElement = new GroovyDummyElement(elementType, text);
    final DummyHolder holder = new DummyHolder(myManager, dummyElement, context);
    final PsiElement element = holder.getFirstChild();
    final T result = ObjectUtils.tryCast(element, elementClass);
    if (result == null) {
      throw new IncorrectOperationException("Cannot create '" + elementClass.getName() + "' from text '" + text + "'");
    }
    GeneratedMarkerVisitor.markGenerated(result);
    return result;
  }
}
