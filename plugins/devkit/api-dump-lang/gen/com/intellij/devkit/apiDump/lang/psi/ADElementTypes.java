// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// This is a generated file. Not intended for manual editing.
package com.intellij.devkit.apiDump.lang.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.devkit.apiDump.lang.elementTypes.ADElementType;
import com.intellij.devkit.apiDump.lang.elementTypes.ADTokenType;
import com.intellij.devkit.apiDump.lang.psi.impl.*;
import com.intellij.psi.impl.source.tree.CompositePsiElement;

public interface ADElementTypes {

  IElementType ARRAY = new ADElementType("ARRAY");
  IElementType CLASS_DECLARATION = new ADElementType("CLASS_DECLARATION");
  IElementType CLASS_HEADER = new ADElementType("CLASS_HEADER");
  IElementType COMPANION = new ADElementType("COMPANION");
  IElementType CONSTRUCTOR = new ADElementType("CONSTRUCTOR");
  IElementType CONSTRUCTOR_REFERENCE = new ADElementType("CONSTRUCTOR_REFERENCE");
  IElementType EXPERIMENTAL = new ADElementType("EXPERIMENTAL");
  IElementType FIELD = new ADElementType("FIELD");
  IElementType FIELD_REFERENCE = new ADElementType("FIELD_REFERENCE");
  IElementType MEMBER = new ADElementType("MEMBER");
  IElementType METHOD = new ADElementType("METHOD");
  IElementType METHOD_REFERENCE = new ADElementType("METHOD_REFERENCE");
  IElementType MODIFIER = new ADElementType("MODIFIER");
  IElementType MODIFIERS = new ADElementType("MODIFIERS");
  IElementType PARAMETER = new ADElementType("PARAMETER");
  IElementType PARAMETERS = new ADElementType("PARAMETERS");
  IElementType SUPER_TYPE = new ADElementType("SUPER_TYPE");
  IElementType TYPE_REFERENCE = new ADElementType("TYPE_REFERENCE");

  IElementType ASTERISK = new ADTokenType("*");
  IElementType AT = new ADTokenType("@");
  IElementType COLON = new ADTokenType(":");
  IElementType COMMA = new ADTokenType(",");
  IElementType DOT = new ADTokenType(".");
  IElementType IDENTIFIER = new ADTokenType("IDENTIFIER");
  IElementType LBRACKET = new ADTokenType("[");
  IElementType LESS = new ADTokenType("<");
  IElementType LPAREN = new ADTokenType("(");
  IElementType MINUS = new ADTokenType("-");
  IElementType MORE = new ADTokenType(">");
  IElementType RBRACKET = new ADTokenType("]");
  IElementType RPAREN = new ADTokenType(")");

  class Factory {
    public static CompositePsiElement createElement(IElementType type) {
       if (type == ARRAY) {
        return new ADArrayImplGen(type);
      }
      else if (type == CLASS_DECLARATION) {
        return new ADClassDeclarationImplGen(type);
      }
      else if (type == CLASS_HEADER) {
        return new ADClassHeaderImplGen(type);
      }
      else if (type == COMPANION) {
        return new ADCompanionImplGen(type);
      }
      else if (type == CONSTRUCTOR) {
        return new ADConstructorImplGen(type);
      }
      else if (type == CONSTRUCTOR_REFERENCE) {
        return new ADConstructorReferenceImplGen(type);
      }
      else if (type == EXPERIMENTAL) {
        return new ADExperimentalImplGen(type);
      }
      else if (type == FIELD) {
        return new ADFieldImplGen(type);
      }
      else if (type == FIELD_REFERENCE) {
        return new ADFieldReferenceImplGen(type);
      }
      else if (type == METHOD) {
        return new ADMethodImplGen(type);
      }
      else if (type == METHOD_REFERENCE) {
        return new ADMethodReferenceImplGen(type);
      }
      else if (type == MODIFIER) {
        return new ADModifierImplGen(type);
      }
      else if (type == MODIFIERS) {
        return new ADModifiersImplGen(type);
      }
      else if (type == PARAMETER) {
        return new ADParameterImplGen(type);
      }
      else if (type == PARAMETERS) {
        return new ADParametersImplGen(type);
      }
      else if (type == SUPER_TYPE) {
        return new ADSuperTypeImplGen(type);
      }
      else if (type == TYPE_REFERENCE) {
        return new ADTypeReferenceImplGen(type);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
