// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.documentation;

import com.intellij.codeInsight.javadoc.JavaDocHighlightingManager;
import com.intellij.ide.highlighter.JavaHighlightingColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.highlighter.GroovySyntaxHighlighter;


public class GroovyDocHighlightingManager implements JavaDocHighlightingManager {

  private static final @NotNull GroovyDocHighlightingManager INSTANCE = new GroovyDocHighlightingManager();

  public static @NotNull GroovyDocHighlightingManager getInstance() {
    return INSTANCE;
  }

  private static @NotNull TextAttributes resolveAttributes(@NotNull TextAttributesKey attributesKey) {
    return EditorColorsManager.getInstance().getGlobalScheme().getAttributes(attributesKey);
  }

  @Override
  public @NotNull TextAttributes getClassDeclarationAttributes(@NotNull PsiClass aClass) {
    if (aClass.isInterface()) return getInterfaceNameAttributes();
    if (aClass.isEnum()) return getEnumNameAttributes();
    if (aClass instanceof PsiAnonymousClass) return getAnonymousClassNameAttributes();
    if (aClass.hasModifierProperty(PsiModifier.ABSTRACT)) return getAbstractClassNameAttributes();
    return getClassNameAttributes();
  }

  @Override
  public @NotNull TextAttributes getMethodDeclarationAttributes(@NotNull PsiMethod method) {
    return method.isConstructor()
           ? getConstructorDeclarationAttributes()
           : getMethodDeclarationAttributes();
  }

  @Override
  public @NotNull TextAttributes getFieldDeclarationAttributes(@NotNull PsiField field) {
    boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
    return isStatic ? getStaticFieldAttributes() : getInstanceFieldAttributes();
  }

  public @NotNull TextAttributes getInterfaceNameAttributes() {
    return resolveAttributes(GroovySyntaxHighlighter.INTERFACE_NAME);
  }

  public @NotNull TextAttributes getEnumNameAttributes() {
    return resolveAttributes(GroovySyntaxHighlighter.ENUM_NAME);
  }

  public @NotNull TextAttributes getAnonymousClassNameAttributes() {
    return resolveAttributes(GroovySyntaxHighlighter.ANONYMOUS_CLASS_NAME);
  }

  public @NotNull TextAttributes getAbstractClassNameAttributes() {
    return resolveAttributes(GroovySyntaxHighlighter.ABSTRACT_CLASS_NAME);
  }

  @Override
  public @NotNull TextAttributes getClassNameAttributes() {
    return resolveAttributes(GroovySyntaxHighlighter.CLASS_REFERENCE);
  }

  @Override
  public @NotNull TextAttributes getKeywordAttributes() {
    return resolveAttributes(GroovySyntaxHighlighter.KEYWORD);
  }

  @Override
  public @NotNull TextAttributes getCommaAttributes() {
    return resolveAttributes(JavaHighlightingColors.COMMA);
  }

  @Override
  public @NotNull TextAttributes getParameterAttributes() {
    return resolveAttributes(GroovySyntaxHighlighter.PARAMETER);
  }

  @Override
  public @NotNull TextAttributes getTypeParameterNameAttributes() {
    return resolveAttributes(GroovySyntaxHighlighter.TYPE_PARAMETER);
  }

  public @NotNull TextAttributes getStaticFieldAttributes() {
    return resolveAttributes(GroovySyntaxHighlighter.STATIC_FIELD);
  }

  public @NotNull TextAttributes getInstanceFieldAttributes() {
    return resolveAttributes(GroovySyntaxHighlighter.INSTANCE_FIELD);
  }

  @Override
  public @NotNull TextAttributes getOperationSignAttributes() {
    return resolveAttributes(GroovySyntaxHighlighter.OPERATION_SIGN);
  }

  @Override
  public @NotNull TextAttributes getLocalVariableAttributes() {
    return resolveAttributes(GroovySyntaxHighlighter.LOCAL_VARIABLE);
  }

  public @NotNull TextAttributes getConstructorDeclarationAttributes() {
    return resolveAttributes(GroovySyntaxHighlighter.CONSTRUCTOR_DECLARATION);
  }

  public @NotNull TextAttributes getMethodDeclarationAttributes() {
    return resolveAttributes(GroovySyntaxHighlighter.METHOD_DECLARATION);
  }

  @Override
  public @NotNull TextAttributes getParenthesesAttributes() {
    return resolveAttributes(GroovySyntaxHighlighter.PARENTHESES);
  }

  @Override
  public @NotNull TextAttributes getDotAttributes() {
    return resolveAttributes(JavaHighlightingColors.DOT);
  }

  @Override
  public @NotNull TextAttributes getBracketsAttributes() {
    return resolveAttributes(GroovySyntaxHighlighter.BRACKETS);
  }

  @Override
  public @NotNull TextAttributes getMethodCallAttributes() {
    return resolveAttributes(GroovySyntaxHighlighter.METHOD_CALL);
  }
  
}
