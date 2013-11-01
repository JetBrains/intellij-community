// This is a generated file. Not intended for manual editing.
package com.jetbrains.json;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import com.intellij.lang.PsiParser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

import static com.jetbrains.json.JsonParserTypes.*;
import static com.jetbrains.json.JsonParserUtil.*;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class JsonParser implements PsiParser {

  public static final Logger LOG_ = Logger.getInstance("com.jetbrains.json.JsonParser");

  public ASTNode parse(IElementType root_, PsiBuilder builder_) {
    int level_ = 0;
    boolean result_;
    builder_ = adapt_builder_(root_, builder_, this, EXTENDS_SETS_);
    if (root_ == ARRAY) {
      result_ = array(builder_, level_ + 1);
    }
    else if (root_ == BOOLEAN_LITERAL) {
      result_ = boolean_literal(builder_, level_ + 1);
    }
    else if (root_ == LITERAL) {
      result_ = literal(builder_, level_ + 1);
    }
    else if (root_ == NULL_LITERAL) {
      result_ = null_literal(builder_, level_ + 1);
    }
    else if (root_ == NUMBER_LITERAL) {
      result_ = number_literal(builder_, level_ + 1);
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
    else if (root_ == STRING_LITERAL) {
      result_ = string_literal(builder_, level_ + 1);
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
    create_token_set_(BOOLEAN_LITERAL, LITERAL, NULL_LITERAL, NUMBER_LITERAL,
      STRING_LITERAL),
    create_token_set_(ARRAY, BOOLEAN_LITERAL, LITERAL, NULL_LITERAL,
      NUMBER_LITERAL, OBJECT, PROPERTY_VALUE, STRING_LITERAL),
  };

  /* ********************************************************** */
  // L_BRAKET (property_value (COMMA property_value)*)? R_BRAKET
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

  // (property_value (COMMA property_value)*)?
  private static boolean array_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_1")) return false;
    array_1_0(builder_, level_ + 1);
    return true;
  }

  // property_value (COMMA property_value)*
  private static boolean array_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_1_0")) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = property_value(builder_, level_ + 1);
    result_ = result_ && array_1_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA property_value)*
  private static boolean array_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_1_0_1")) return false;
    int offset_ = builder_.getCurrentOffset();
    while (true) {
      if (!array_1_0_1_0(builder_, level_ + 1)) break;
      int next_offset_ = builder_.getCurrentOffset();
      if (offset_ == next_offset_) {
        empty_element_parsed_guard_(builder_, offset_, "array_1_0_1");
        break;
      }
      offset_ = next_offset_;
    }
    return true;
  }

  // COMMA property_value
  private static boolean array_1_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "array_1_0_1_0")) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, COMMA);
    result_ = result_ && property_value(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  /* ********************************************************** */
  // TRUE | FALSE
  public static boolean boolean_literal(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "boolean_literal")) return false;
    if (!nextTokenIs(builder_, FALSE) && !nextTokenIs(builder_, TRUE)
        && replaceVariants(builder_, 2, "<boolean literal>")) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, "<boolean literal>");
    result_ = consumeToken(builder_, TRUE);
    if (!result_) result_ = consumeToken(builder_, FALSE);
    exit_section_(builder_, level_, marker_, BOOLEAN_LITERAL, result_, false, null);
    return result_;
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
  // string_literal | number_literal | boolean_literal | null_literal
  public static boolean literal(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "literal")) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_, level_, _COLLAPSE_, "<literal>");
    result_ = string_literal(builder_, level_ + 1);
    if (!result_) result_ = number_literal(builder_, level_ + 1);
    if (!result_) result_ = boolean_literal(builder_, level_ + 1);
    if (!result_) result_ = null_literal(builder_, level_ + 1);
    exit_section_(builder_, level_, marker_, LITERAL, result_, false, null);
    return result_;
  }

  /* ********************************************************** */
  // NULL
  public static boolean null_literal(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "null_literal")) return false;
    if (!nextTokenIs(builder_, NULL)) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NULL);
    exit_section_(builder_, marker_, NULL_LITERAL, result_);
    return result_;
  }

  /* ********************************************************** */
  // NUMBER
  public static boolean number_literal(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "number_literal")) return false;
    if (!nextTokenIs(builder_, NUMBER)) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, NUMBER);
    exit_section_(builder_, marker_, NUMBER_LITERAL, result_);
    return result_;
  }

  /* ********************************************************** */
  // L_CURLY (property (COMMA property)*)? R_CURLY
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

  // (property (COMMA property)*)?
  private static boolean object_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_1")) return false;
    object_1_0(builder_, level_ + 1);
    return true;
  }

  // property (COMMA property)*
  private static boolean object_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_1_0")) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = property(builder_, level_ + 1);
    result_ = result_ && object_1_0_1(builder_, level_ + 1);
    exit_section_(builder_, marker_, null, result_);
    return result_;
  }

  // (COMMA property)*
  private static boolean object_1_0_1(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_1_0_1")) return false;
    int offset_ = builder_.getCurrentOffset();
    while (true) {
      if (!object_1_0_1_0(builder_, level_ + 1)) break;
      int next_offset_ = builder_.getCurrentOffset();
      if (offset_ == next_offset_) {
        empty_element_parsed_guard_(builder_, offset_, "object_1_0_1");
        break;
      }
      offset_ = next_offset_;
    }
    return true;
  }

  // COMMA property
  private static boolean object_1_0_1_0(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "object_1_0_1_0")) return false;
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
  // string_literal
  public static boolean property_name(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "property_name")) return false;
    if (!nextTokenIs(builder_, STRING)) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = string_literal(builder_, level_ + 1);
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
  // STRING
  public static boolean string_literal(PsiBuilder builder_, int level_) {
    if (!recursion_guard_(builder_, level_, "string_literal")) return false;
    if (!nextTokenIs(builder_, STRING)) return false;
    boolean result_ = false;
    Marker marker_ = enter_section_(builder_);
    result_ = consumeToken(builder_, STRING);
    exit_section_(builder_, marker_, STRING_LITERAL, result_);
    return result_;
  }

}
