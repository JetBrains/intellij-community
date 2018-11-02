// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

// This is a generated file. Not intended for manual editing.
package com.intellij.openapi.vcs.changes.ignore.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;

import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import static com.intellij.openapi.vcs.changes.ignore.psi.IgnoreTypes.*;

import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class IgnoreParser implements PsiParser {

  public ASTNode parse(IElementType root_, PsiBuilder builder_) {
    parseLight(root_, builder_);
    return builder_.getTreeBuilt();
  }

  public void parseLight(IElementType root_, PsiBuilder builder_) {
    boolean result_;
    builder_ = adapt_builder_(root_, builder_, this, EXTENDS_SETS_);
    Marker marker_ = enter_section_(builder_, 0, _COLLAPSE_, null);
    if (root_ == ENTRY) {
      result_ = ENTRY(builder_, 0);
    }
    else if (root_ == ENTRY_DIRECTORY) {
      result_ = ENTRY_DIRECTORY(builder_, 0);
    }
    else if (root_ == ENTRY_FILE) {
      result_ = ENTRY_FILE(builder_, 0);
    }
    else if (root_ == NEGATION) {
      result_ = NEGATION(builder_, 0);
    }
    else if (root_ == SYNTAX) {
      result_ = SYNTAX(builder_, 0);
    }
    else {
      result_ = parse_root_(root_, builder_, 0);
    }
    exit_section_(builder_, 0, marker_, root_, result_, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType root_, PsiBuilder builder_, int level_) {
    return ignoreFile(builder_, level_ + 1);
  }

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(ENTRY_DIRECTORY, ENTRY_FILE),
    create_token_set_(ENTRY, ENTRY_DIRECTORY, ENTRY_FILE),
  };

  /* ********************************************************** */
  // NEGATION ? SLASH ? <<list_macro value_>>
  public static boolean ENTRY(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ENTRY")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, "<entry>");
    result_ = ENTRY_0(builder_, level_ + 1);
    result_ = result_ && ENTRY_1(builder_, level_ + 1);
    result_ = result_ && list_macro(builder_, level_ + 1, value__parser_);
    exit_section_(builder_, level_, marker_, ENTRY, result_, false, null);
    return result_;
  }

  // NEGATION ?
  private static boolean ENTRY_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ENTRY_0")) return false;
    NEGATION(builder_, level_ + 1);
    return true;
  }

  // SLASH ?
  private static boolean ENTRY_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ENTRY_1")) return false;
    consumeToken(builder_, SLASH);
    return true;
  }

  /* ********************************************************** */
  // NEGATION ? SLASH ? <<list_macro value_>> SLASH
  public static boolean ENTRY_DIRECTORY(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ENTRY_DIRECTORY")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, "<entry>");
    result_ = ENTRY_DIRECTORY_0(builder_, level_ + 1);
    result_ = result_ && ENTRY_DIRECTORY_1(builder_, level_ + 1);
    result_ = result_ && list_macro(builder_, level_ + 1, value__parser_);
    result_ = result_ && consumeToken(builder_, SLASH);
    exit_section_(builder_, level_, marker_, ENTRY_DIRECTORY, result_, false, null);
    return result_;
  }

  // NEGATION ?
  private static boolean ENTRY_DIRECTORY_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ENTRY_DIRECTORY_0")) return false;
    NEGATION(builder_, level_ + 1);
    return true;
  }

  // SLASH ?
  private static boolean ENTRY_DIRECTORY_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ENTRY_DIRECTORY_1")) return false;
    consumeToken(builder_, SLASH);
    return true;
  }

  /* ********************************************************** */
  // NEGATION ? SLASH ? <<list_macro value_>>
  public static boolean ENTRY_FILE(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ENTRY_FILE")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, "<entry>");
    result_ = ENTRY_FILE_0(builder_, level_ + 1);
    result_ = result_ && ENTRY_FILE_1(builder_, level_ + 1);
    result_ = result_ && list_macro(builder_, level_ + 1, value__parser_);
    exit_section_(builder_, level_, marker_, ENTRY_FILE, result_, false, null);
    return result_;
  }

  // NEGATION ?
  private static boolean ENTRY_FILE_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ENTRY_FILE_0")) return false;
    NEGATION(builder_, level_ + 1);
    return true;
  }

  // SLASH ?
  private static boolean ENTRY_FILE_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ENTRY_FILE_1")) return false;
    consumeToken(builder_, SLASH);
    return true;
  }

  /* ********************************************************** */
  // "!"
  public static boolean NEGATION(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "NEGATION")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, "<negation>");
    result_ = consumeToken(builder_, "!");
    exit_section_(builder_, level_, marker_, NEGATION, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // SYNTAX_KEY CRLF * VALUE
  public static boolean SYNTAX(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SYNTAX")) return false;
    if (!nextTokenIs(builder_, SYNTAX_KEY)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SYNTAX_KEY);
    result_ = result_ && SYNTAX_1(builder_, level_ + 1);
    result_ = result_ && consumeToken(builder_, VALUE);
    exit_section_(builder_, marker_, SYNTAX, result_);
    return result_;
  }

  // CRLF *
  private static boolean SYNTAX_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "SYNTAX_1")) return false;
    int pos_ = current_position_(builder_);
    while (true) {
      if (!consumeToken(builder_, CRLF)) break;
      if (!empty_element_parsed_guard_(builder_, "SYNTAX_1", pos_)) break;
      pos_ = current_position_(builder_);
    }
    return true;
  }

  /* ********************************************************** */
  // BRACKET_LEFT ( VALUE SLASH ? ) + BRACKET_RIGHT
  static boolean bvalue_(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bvalue_")) return false;
    if (!nextTokenIs(builder_, BRACKET_LEFT)) return false;
    boolean result_, pinned_;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, null);
    result_ = consumeToken(builder_, BRACKET_LEFT);
    pinned_ = result_; // pin = BRACKET_LEFT
    result_ = result_ && report_error_(builder_, bvalue__1(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, BRACKET_RIGHT) && result_;
    exit_section_(builder_, level_, marker_, null, result_, pinned_, null);
    return result_ || pinned_;
  }

  // ( VALUE SLASH ? ) +
  private static boolean bvalue__1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bvalue__1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bvalue__1_0(builder_, level_ + 1);
    int pos_ = current_position_(builder_);
    while (result_) {
      if (!bvalue__1_0(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "bvalue__1", pos_)) break;
      pos_ = current_position_(builder_);
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // VALUE SLASH ?
  private static boolean bvalue__1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bvalue__1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, VALUE);
    result_ = result_ && bvalue__1_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // SLASH ?
  private static boolean bvalue__1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "bvalue__1_0_1")) return false;
    consumeToken(builder_, SLASH);
    return true;
  }

  /* ********************************************************** */
  // item_ *
  static boolean ignoreFile(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "ignoreFile")) return false;
    int pos_ = current_position_(builder_);
    while (true) {
      if (!item_(builder_, level_ + 1)) break;
      if (!empty_element_parsed_guard_(builder_, "ignoreFile", pos_)) break;
      pos_ = current_position_(builder_);
    }
    return true;
  }

  /* ********************************************************** */
  // HEADER | SECTION | COMMENT | SYNTAX | ENTRY_DIRECTORY | ENTRY_FILE | CRLF
  static boolean item_(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "item_")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, HEADER);
    if (!result_) result_ = consumeToken(builder_, SECTION);
    if (!result_) result_ = consumeToken(builder_, COMMENT);
    if (!result_) result_ = SYNTAX(builder_, level_ + 1);
    if (!result_) result_ = ENTRY_DIRECTORY(builder_, level_ + 1);
    if (!result_) result_ = ENTRY_FILE(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, CRLF);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // <<p>> + (SLASH <<p>> +) *
  static boolean list_macro(PsiBuilder builder_, int level_, final Parser p) {
    if (!recursion_guard_(builder_, level_, "list_macro")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = list_macro_0(builder_, level_ + 1, p);
    result_ = result_ && list_macro_1(builder_, level_ + 1, p);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // <<p>> +
  private static boolean list_macro_0(PsiBuilder builder_, int level_, final Parser p) {
    if (!recursion_guard_(builder_, level_, "list_macro_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = p.parse(builder_, level_);
    int pos_ = current_position_(builder_);
    while (result_) {
      if (!p.parse(builder_, level_)) break;
      if (!empty_element_parsed_guard_(builder_, "list_macro_0", pos_)) break;
      pos_ = current_position_(builder_);
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (SLASH <<p>> +) *
  private static boolean list_macro_1(PsiBuilder builder_, int level_, final Parser p) {
    if (!recursion_guard_(builder_, level_, "list_macro_1")) return false;
    int pos_ = current_position_(builder_);
    while (true) {
      if (!list_macro_1_0(builder_, level_ + 1, p)) break;
      if (!empty_element_parsed_guard_(builder_, "list_macro_1", pos_)) break;
      pos_ = current_position_(builder_);
    }
    return true;
  }

  // SLASH <<p>> +
  private static boolean list_macro_1_0(PsiBuilder builder_, int level_, final Parser p) {
    if (!recursion_guard_(builder_, level_, "list_macro_1_0")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, SLASH);
    result_ = result_ && list_macro_1_0_1(builder_, level_ + 1, p);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // <<p>> +
  private static boolean list_macro_1_0_1(PsiBuilder builder_, int level_, final Parser p) {
    if (!recursion_guard_(builder_, level_, "list_macro_1_0_1")) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = p.parse(builder_, level_);
    int pos_ = current_position_(builder_);
    while (result_) {
      if (!p.parse(builder_, level_)) break;
      if (!empty_element_parsed_guard_(builder_, "list_macro_1_0_1", pos_)) break;
      pos_ = current_position_(builder_);
    }
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // bvalue_ | VALUE
  static boolean value_(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "value_")) return false;
    if (!nextTokenIs(builder_, "", BRACKET_LEFT, VALUE)) return false;
    boolean result_;
    Marker marker_ = enter_section_(builder_);
    result_ = bvalue_(builder_, level_ + 1);
    if (!result_) result_ = consumeToken(builder_, VALUE);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  final static Parser value__parser_ = new Parser() {
    public boolean parse(PsiBuilder builder_, int level_) {
      return value_(builder_, level_ + 1);
    }
  };
}
