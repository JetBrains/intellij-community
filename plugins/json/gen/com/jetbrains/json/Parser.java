// This is a generated file. Not intended for manual editing.
package com.jetbrains.json;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import com.intellij.openapi.diagnostic.Logger;
import static com.jetbrains.json.JsonParserTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class Parser implements PsiParser {

  public static final Logger LOG_ = Logger.getInstance("com.jetbrains.json.Parser");

  public ASTNode parse(IElementType root_, PsiBuilder builder_) {
    int level_ = 0;
    boolean result_;
    builder_ = adapt_builder_(root_, builder_, this, EXTENDS_SETS_);
    if (root_ == ARRAY) {
      result_ = array(builder_, level_ + 1);
    }
    else if (root_ == LITERAL) {
      result_ = literal(builder_, level_ + 1);
    }
    else if (root_ == OBJECT) {
      result_ = object(builder_, level_ + 1);
    }
    else if (root_ == PROPERTY) {
      result_ = property(builder_, level_ + 1);
    }
    else if (root_ == PROPERTY_NAME) {
      result_ = property_name(builder_, level_ + 1);
    }
    else if (root_ == PROPERTY_VALUE) {
      result_ = property_value(builder_, level_ + 1);
    }
    else {
      Marker marker_ = enter_section_(builder_, level_, _NONE_, null);
      result_ = parse_root_(root_, builder_, level_);
      exit_section_(builder_, level_, marker_, root_, result_, true, TOKEN_ADVANCER);
    }
    return builder_.getTreeBuilt();
  }

  protected boolean parse_root_(final IElementType root_, final PsiBuilder builder_, final int level_) {
    return json(builder_, level_ + 1);
  }

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(ARRAY, LITERAL, OBJECT, PROPERTY_VALUE),
  };

  /* ********************************************************** */
  // L_BRAKET values? R_BRAKET
  public static boolean array(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array")) return false;
    if (!nextTokenIs(builder_, L_BRAKET)) return false;
    boolean result_ = false;
    boolean pinned_ = false;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, null);
    result_ = consumeToken(builder_, L_BRAKET);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, array_1(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, R_BRAKET) && result_;
    exit_section_(builder_, level_, marker_, ARRAY, result_, pinned_, null);
    return result_ || pinned_;
  }

  // values?
  private static boolean array_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_1")) return false;
    values(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // object | array
  static boolean json(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "json")) return false;
    if (!nextTokenIs(builder_, L_BRAKET) && !nextTokenIs(builder_, L_CURLY)) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = object(builder_, level_ + 1);
    if (!result_) result_ = array(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // STRING | NUMBER | TRUE | FALSE | NULL
  public static boolean literal(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "literal")) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, "<literal>");
    result_ = consumeToken(builder_, STRING);
    if (!result_) result_ = consumeToken(builder_, NUMBER);
    if (!result_) result_ = consumeToken(builder_, TRUE);
    if (!result_) result_ = consumeToken(builder_, FALSE);
    if (!result_) result_ = consumeToken(builder_, NULL);
    exit_section_(builder_, level_, marker_, LITERAL, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // L_CURLY properties? R_CURLY
  public static boolean object(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object")) return false;
    if (!nextTokenIs(builder_, L_CURLY)) return false;
    boolean result_ = false;
    boolean pinned_ = false;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, null);
    result_ = consumeToken(builder_, L_CURLY);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, object_1(builder_, level_ + 1));
    result_ = pinned_ && consumeToken(builder_, R_CURLY) && result_;
    exit_section_(builder_, level_, marker_, OBJECT, result_, pinned_, null);
    return result_ || pinned_;
  }

  // properties?
  private static boolean object_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_1")) return false;
    properties(builder_, level_ + 1);
    return true;
  }

  /* ********************************************************** */
  // property (COMMA property)*
  static boolean properties(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "properties")) return false;
    if (!nextTokenIs(builder_, STRING)) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = property(builder_, level_ + 1);
    result_ = result_ && properties_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA property)*
  private static boolean properties_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "properties_1")) return false;
    int offset_ = builder_.getCurrentOffset();
    while (true) {
      if (!properties_1_0(builder_, level_ + 1)) break;
      int next_offset_ = builder_.getCurrentOffset();
      if (offset_ == next_offset_) {
        empty_element_parsed_guard_(builder_, offset_, "properties_1");
        break;
      }
      offset_ = next_offset_;
    }
    return true;
  }

  // COMMA property
  private static boolean properties_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "properties_1_0")) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && property(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // property_name COLON property_value
  public static boolean property(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "property")) return false;
    if (!nextTokenIs(builder_, STRING)) return false;
    boolean result_ = false;
    boolean pinned_ = false;
    Marker marker_ = enter_section_(builder_, level_, _NONE_, null);
    result_ = property_name(builder_, level_ + 1);
    pinned_ = result_; // pin = 1
    result_ = result_ && report_error_(builder_, consumeToken(builder_, COLON));
    result_ = pinned_ && property_value(builder_, level_ + 1) && result_;
    exit_section_(builder_, level_, marker_, PROPERTY, result_, pinned_, null);
    return result_ || pinned_;
  }

  /* ********************************************************** */
  // STRING
  public static boolean property_name(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "property_name")) return false;
    if (!nextTokenIs(builder_, STRING)) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STRING);
    exit_section_(builder_, marker_, PROPERTY_NAME, result_);
    return result_;
  }

  /* ********************************************************** */
  // object | array | literal
  public static boolean property_value(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "property_value")) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, "<property value>");
    result_ = object(builder_, level_ + 1);
    if (!result_) result_ = array(builder_, level_ + 1);
    if (!result_) result_ = literal(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, PROPERTY_VALUE, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // property_value (COMMA property_value)*
  static boolean values(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "values")) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = property_value(builder_, level_ + 1);
    result_ = result_ && values_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA property_value)*
  private static boolean values_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "values_1")) return false;
    int offset_ = builder_.getCurrentOffset();
    while (true) {
      if (!values_1_0(builder_, level_ + 1)) break;
      int next_offset_ = builder_.getCurrentOffset();
      if (offset_ == next_offset_) {
        empty_element_parsed_guard_(builder_, offset_, "values_1");
        break;
      }
      offset_ = next_offset_;
    }
    return true;
  }

  // COMMA property_value
  private static boolean values_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "values_1_0")) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && property_value(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

}
