// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.editorconfig.language.psi.impl.*;

public interface EditorConfigElementTypes {

  IElementType ASTERISK_PATTERN = new EditorConfigElementType("ASTERISK_PATTERN");
  IElementType CHAR_CLASS = new EditorConfigElementType("CHAR_CLASS");
  IElementType CHAR_CLASS_EXCLAMATION = new EditorConfigElementType("CHAR_CLASS_EXCLAMATION");
  IElementType CHAR_CLASS_LETTER = new EditorConfigElementType("CHAR_CLASS_LETTER");
  IElementType DOUBLE_ASTERISK_PATTERN = new EditorConfigElementType("DOUBLE_ASTERISK_PATTERN");
  IElementType FLAT_OPTION_KEY = new EditorConfigElementType("FLAT_OPTION_KEY");
  IElementType FLAT_PATTERN = new EditorConfigElementType("FLAT_PATTERN");
  IElementType HEADER = new EditorConfigElementType("HEADER");
  IElementType OPTION = new EditorConfigElementType("OPTION");
  IElementType OPTION_VALUE_IDENTIFIER = new EditorConfigElementType("OPTION_VALUE_IDENTIFIER");
  IElementType OPTION_VALUE_LIST = new EditorConfigElementType("OPTION_VALUE_LIST");
  IElementType OPTION_VALUE_PAIR = new EditorConfigElementType("OPTION_VALUE_PAIR");
  IElementType PATTERN = new EditorConfigElementType("PATTERN");
  IElementType PATTERN_ENUMERATION = new EditorConfigElementType("PATTERN_ENUMERATION");
  IElementType QUALIFIED_KEY_PART = new EditorConfigElementType("QUALIFIED_KEY_PART");
  IElementType QUALIFIED_OPTION_KEY = new EditorConfigElementType("QUALIFIED_OPTION_KEY");
  IElementType QUESTION_PATTERN = new EditorConfigElementType("QUESTION_PATTERN");
  IElementType RAW_OPTION_VALUE = new EditorConfigElementType("RAW_OPTION_VALUE");
  IElementType ROOT_DECLARATION = new EditorConfigElementType("ROOT_DECLARATION");
  IElementType ROOT_DECLARATION_KEY = new EditorConfigElementType("ROOT_DECLARATION_KEY");
  IElementType ROOT_DECLARATION_VALUE = new EditorConfigElementType("ROOT_DECLARATION_VALUE");
  IElementType SECTION = new EditorConfigElementType("SECTION");

  IElementType ASTERISK = new EditorConfigTokenType("ASTERISK");
  IElementType CHARCLASS_LETTER = new EditorConfigTokenType("CHARCLASS_LETTER");
  IElementType COLON = new EditorConfigTokenType("COLON");
  IElementType COMMA = new EditorConfigTokenType("COMMA");
  IElementType DOT = new EditorConfigTokenType("DOT");
  IElementType DOUBLE_ASTERISK = new EditorConfigTokenType("DOUBLE_ASTERISK");
  IElementType EXCLAMATION = new EditorConfigTokenType("EXCLAMATION");
  IElementType IDENTIFIER = new EditorConfigTokenType("IDENTIFIER");
  IElementType LINE_COMMENT = new EditorConfigTokenType("LINE_COMMENT");
  IElementType L_BRACKET = new EditorConfigTokenType("L_BRACKET");
  IElementType L_CURLY = new EditorConfigTokenType("L_CURLY");
  IElementType PATTERN_IDENTIFIER = new EditorConfigTokenType("PATTERN_IDENTIFIER");
  IElementType QUESTION = new EditorConfigTokenType("QUESTION");
  IElementType R_BRACKET = new EditorConfigTokenType("R_BRACKET");
  IElementType R_CURLY = new EditorConfigTokenType("R_CURLY");
  IElementType SEPARATOR = new EditorConfigTokenType("SEPARATOR");

  final class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == ASTERISK_PATTERN) {
        return new EditorConfigAsteriskPatternImpl(node);
      }
      else if (type == CHAR_CLASS) {
        return new EditorConfigCharClassImpl(node);
      }
      else if (type == CHAR_CLASS_EXCLAMATION) {
        return new EditorConfigCharClassExclamationImpl(node);
      }
      else if (type == CHAR_CLASS_LETTER) {
        return new EditorConfigCharClassLetterImpl(node);
      }
      else if (type == DOUBLE_ASTERISK_PATTERN) {
        return new EditorConfigDoubleAsteriskPatternImpl(node);
      }
      else if (type == FLAT_OPTION_KEY) {
        return new EditorConfigFlatOptionKeyImpl(node);
      }
      else if (type == FLAT_PATTERN) {
        return new EditorConfigFlatPatternImpl(node);
      }
      else if (type == HEADER) {
        return new EditorConfigHeaderImpl(node);
      }
      else if (type == OPTION) {
        return new EditorConfigOptionImpl(node);
      }
      else if (type == OPTION_VALUE_IDENTIFIER) {
        return new EditorConfigOptionValueIdentifierImpl(node);
      }
      else if (type == OPTION_VALUE_LIST) {
        return new EditorConfigOptionValueListImpl(node);
      }
      else if (type == OPTION_VALUE_PAIR) {
        return new EditorConfigOptionValuePairImpl(node);
      }
      else if (type == PATTERN) {
        return new EditorConfigPatternImpl(node);
      }
      else if (type == PATTERN_ENUMERATION) {
        return new EditorConfigPatternEnumerationImpl(node);
      }
      else if (type == QUALIFIED_KEY_PART) {
        return new EditorConfigQualifiedKeyPartImpl(node);
      }
      else if (type == QUALIFIED_OPTION_KEY) {
        return new EditorConfigQualifiedOptionKeyImpl(node);
      }
      else if (type == QUESTION_PATTERN) {
        return new EditorConfigQuestionPatternImpl(node);
      }
      else if (type == RAW_OPTION_VALUE) {
        return new EditorConfigRawOptionValueImpl(node);
      }
      else if (type == ROOT_DECLARATION) {
        return new EditorConfigRootDeclarationImpl(node);
      }
      else if (type == ROOT_DECLARATION_KEY) {
        return new EditorConfigRootDeclarationKeyImpl(node);
      }
      else if (type == ROOT_DECLARATION_VALUE) {
        return new EditorConfigRootDeclarationValueImpl(node);
      }
      else if (type == SECTION) {
        return new EditorConfigSectionImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
