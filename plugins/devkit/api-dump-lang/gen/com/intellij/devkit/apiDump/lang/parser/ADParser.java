// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// This is a generated file. Not intended for manual editing.
package com.intellij.devkit.apiDump.lang.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.intellij.devkit.apiDump.lang.psi.ADElementTypes.*;
import static com.intellij.devkit.apiDump.lang.parser.ADParserUtil.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class ADParser implements PsiParser, LightPsiParser {

  public ASTNode parse(IElementType t, PsiBuilder b) {
    parseLight(t, b);
    return b.getTreeBuilt();
  }

  public void parseLight(IElementType t, PsiBuilder b) {
    boolean r;
    b = adapt_builder_(t, b, this, EXTENDS_SETS_);
    Marker m = enter_section_(b, 0, _COLLAPSE_, null);
    r = parse_root_(t, b);
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b) {
    return parse_root_(t, b, 0);
  }

  static boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    return File(b, l + 1);
  }

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(COMPANION, CONSTRUCTOR, FIELD, MEMBER,
      METHOD, SUPER_TYPE),
  };

  /* ********************************************************** */
  // LBRACKET RBRACKET
  public static boolean Array(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Array")) return false;
    if (!nextTokenIs(b, LBRACKET)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, LBRACKET, RBRACKET);
    exit_section_(b, m, ARRAY, r);
    return r;
  }

  /* ********************************************************** */
  // ClassHeader Member*
  public static boolean ClassDeclaration(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ClassDeclaration")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, CLASS_DECLARATION, "<class declaration>");
    r = ClassHeader(b, l + 1);
    r = r && ClassDeclaration_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // Member*
  private static boolean ClassDeclaration_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ClassDeclaration_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!Member(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "ClassDeclaration_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // Modifiers? TypeReference
  public static boolean ClassHeader(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ClassHeader")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, CLASS_HEADER, "<class header>");
    r = ClassHeader_0(b, l + 1);
    r = r && TypeReference(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // Modifiers?
  private static boolean ClassHeader_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ClassHeader_0")) return false;
    Modifiers(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // MemberStart 'Companion' COLON TypeReference
  public static boolean Companion(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Companion")) return false;
    if (!nextTokenIs(b, MINUS)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = MemberStart(b, l + 1);
    r = r && consumeToken(b, "Companion");
    r = r && consumeToken(b, COLON);
    r = r && TypeReference(b, l + 1);
    exit_section_(b, m, COMPANION, r);
    return r;
  }

  /* ********************************************************** */
  // MemberStart ConstructorReference Parameters TypeAnnotation
  public static boolean Constructor(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Constructor")) return false;
    if (!nextTokenIs(b, MINUS)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, CONSTRUCTOR, null);
    r = MemberStart(b, l + 1);
    r = r && ConstructorReference(b, l + 1);
    r = r && Parameters(b, l + 1);
    p = r; // pin = Parameters
    r = r && TypeAnnotation(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // LESS 'init' MORE
  public static boolean ConstructorReference(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ConstructorReference")) return false;
    if (!nextTokenIs(b, LESS)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LESS);
    r = r && consumeToken(b, "init");
    r = r && consumeToken(b, MORE);
    exit_section_(b, m, CONSTRUCTOR_REFERENCE, r);
    return r;
  }

  /* ********************************************************** */
  // ASTERISK
  public static boolean Experimental(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Experimental")) return false;
    if (!nextTokenIs(b, ASTERISK)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ASTERISK);
    exit_section_(b, m, EXPERIMENTAL, r);
    return r;
  }

  /* ********************************************************** */
  // MemberStart FieldReference TypeAnnotation
  public static boolean Field(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Field")) return false;
    if (!nextTokenIs(b, MINUS)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = MemberStart(b, l + 1);
    r = r && FieldReference(b, l + 1);
    r = r && TypeAnnotation(b, l + 1);
    exit_section_(b, m, FIELD, r);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER
  public static boolean FieldReference(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "FieldReference")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    exit_section_(b, m, FIELD_REFERENCE, r);
    return r;
  }

  /* ********************************************************** */
  // ClassDeclaration*
  static boolean File(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "File")) return false;
    while (true) {
      int c = current_position_(b);
      if (!ClassDeclaration(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "File", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // Method | Constructor | Field | Companion | SuperType
  public static boolean Member(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Member")) return false;
    if (!nextTokenIs(b, MINUS)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, MEMBER, null);
    r = Method(b, l + 1);
    if (!r) r = Constructor(b, l + 1);
    if (!r) r = Field(b, l + 1);
    if (!r) r = Companion(b, l + 1);
    if (!r) r = SuperType(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // MINUS (Modifiers &(IDENTIFIER (LPAREN | COLON) | LESS))?
  static boolean MemberStart(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "MemberStart")) return false;
    if (!nextTokenIs(b, MINUS)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, MINUS);
    r = r && MemberStart_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (Modifiers &(IDENTIFIER (LPAREN | COLON) | LESS))?
  private static boolean MemberStart_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "MemberStart_1")) return false;
    MemberStart_1_0(b, l + 1);
    return true;
  }

  // Modifiers &(IDENTIFIER (LPAREN | COLON) | LESS)
  private static boolean MemberStart_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "MemberStart_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = Modifiers(b, l + 1);
    r = r && MemberStart_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // &(IDENTIFIER (LPAREN | COLON) | LESS)
  private static boolean MemberStart_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "MemberStart_1_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = MemberStart_1_0_1_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // IDENTIFIER (LPAREN | COLON) | LESS
  private static boolean MemberStart_1_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "MemberStart_1_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = MemberStart_1_0_1_0_0(b, l + 1);
    if (!r) r = consumeToken(b, LESS);
    exit_section_(b, m, null, r);
    return r;
  }

  // IDENTIFIER (LPAREN | COLON)
  private static boolean MemberStart_1_0_1_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "MemberStart_1_0_1_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    r = r && MemberStart_1_0_1_0_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // LPAREN | COLON
  private static boolean MemberStart_1_0_1_0_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "MemberStart_1_0_1_0_0_1")) return false;
    boolean r;
    r = consumeToken(b, LPAREN);
    if (!r) r = consumeToken(b, COLON);
    return r;
  }

  /* ********************************************************** */
  // MemberStart MethodReference Parameters TypeAnnotation
  public static boolean Method(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Method")) return false;
    if (!nextTokenIs(b, MINUS)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, METHOD, null);
    r = MemberStart(b, l + 1);
    r = r && MethodReference(b, l + 1);
    r = r && Parameters(b, l + 1);
    p = r; // pin = Parameters
    r = r && TypeAnnotation(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // IDENTIFIER
  public static boolean MethodReference(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "MethodReference")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    exit_section_(b, m, METHOD_REFERENCE, r);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER | AT
  public static boolean Modifier(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Modifier")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, MODIFIER, "<modifier>");
    r = consumeToken(b, IDENTIFIER);
    if (!r) r = consumeToken(b, AT);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // &(ASTERISK | IDENTIFIER | AT) Experimental? Modifier? COLON
  public static boolean Modifiers(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Modifiers")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, MODIFIERS, "<modifiers>");
    r = Modifiers_0(b, l + 1);
    r = r && Modifiers_1(b, l + 1);
    r = r && Modifiers_2(b, l + 1);
    r = r && consumeToken(b, COLON);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // &(ASTERISK | IDENTIFIER | AT)
  private static boolean Modifiers_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Modifiers_0")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _AND_);
    r = Modifiers_0_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // ASTERISK | IDENTIFIER | AT
  private static boolean Modifiers_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Modifiers_0_0")) return false;
    boolean r;
    r = consumeToken(b, ASTERISK);
    if (!r) r = consumeToken(b, IDENTIFIER);
    if (!r) r = consumeToken(b, AT);
    return r;
  }

  // Experimental?
  private static boolean Modifiers_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Modifiers_1")) return false;
    Experimental(b, l + 1);
    return true;
  }

  // Modifier?
  private static boolean Modifiers_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Modifiers_2")) return false;
    Modifier(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // TypeReference
  public static boolean Parameter(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Parameter")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = TypeReference(b, l + 1);
    exit_section_(b, m, PARAMETER, r);
    return r;
  }

  /* ********************************************************** */
  // LPAREN Parameter? (COMMA Parameter)* RPAREN
  public static boolean Parameters(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Parameters")) return false;
    if (!nextTokenIs(b, LPAREN)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, PARAMETERS, null);
    r = consumeToken(b, LPAREN);
    p = r; // pin = LPAREN
    r = r && report_error_(b, Parameters_1(b, l + 1));
    r = p && report_error_(b, Parameters_2(b, l + 1)) && r;
    r = p && consumeToken(b, RPAREN) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // Parameter?
  private static boolean Parameters_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Parameters_1")) return false;
    Parameter(b, l + 1);
    return true;
  }

  // (COMMA Parameter)*
  private static boolean Parameters_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Parameters_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!Parameters_2_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "Parameters_2", c)) break;
    }
    return true;
  }

  // COMMA Parameter
  private static boolean Parameters_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "Parameters_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && Parameter(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // MINUS TypeReference
  public static boolean SuperType(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "SuperType")) return false;
    if (!nextTokenIs(b, MINUS)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, MINUS);
    r = r && TypeReference(b, l + 1);
    exit_section_(b, m, SUPER_TYPE, r);
    return r;
  }

  /* ********************************************************** */
  // COLON TypeReference
  static boolean TypeAnnotation(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "TypeAnnotation")) return false;
    if (!nextTokenIs(b, COLON)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, COLON);
    p = r; // pin = COLON
    r = r && TypeReference(b, l + 1);
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  /* ********************************************************** */
  // IDENTIFIER (DOT IDENTIFIER)* Array*
  public static boolean TypeReference(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "TypeReference")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    r = r && TypeReference_1(b, l + 1);
    r = r && TypeReference_2(b, l + 1);
    exit_section_(b, m, TYPE_REFERENCE, r);
    return r;
  }

  // (DOT IDENTIFIER)*
  private static boolean TypeReference_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "TypeReference_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!TypeReference_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "TypeReference_1", c)) break;
    }
    return true;
  }

  // DOT IDENTIFIER
  private static boolean TypeReference_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "TypeReference_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeTokens(b, 0, DOT, IDENTIFIER);
    exit_section_(b, m, null, r);
    return r;
  }

  // Array*
  private static boolean TypeReference_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "TypeReference_2")) return false;
    while (true) {
      int c = current_position_(b);
      if (!Array(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "TypeReference_2", c)) break;
    }
    return true;
  }

}
