// This is a generated file. Not intended for manual editing.
package com.intellij.editorconfig.common.syntax.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.intellij.editorconfig.common.syntax.psi.EditorConfigElementTypes.*;
import static com.intellij.editorconfig.common.syntax.parser.EditorConfigParserUtil.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class EditorConfigParserBase implements PsiParser, LightPsiParser {

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
    return editorConfigFile(b, l + 1);
  }

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(ASTERISK_PATTERN, CHAR_CLASS_PATTERN, CONCATENATED_PATTERN, DOUBLE_ASTERISK_PATTERN,
      ENUMERATION_PATTERN, FLAT_PATTERN, QUESTION_PATTERN),
  };

  /* ********************************************************** */
  // ASTERISK
  public static boolean asterisk_pattern(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "asterisk_pattern")) return false;
    if (!nextTokenIs(b, ASTERISK)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, ASTERISK);
    exit_section_(b, m, ASTERISK_PATTERN, r);
    return r;
  }

  /* ********************************************************** */
  // EXCLAMATION
  public static boolean char_class_exclamation(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "char_class_exclamation")) return false;
    if (!nextTokenIs(b, EXCLAMATION)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, EXCLAMATION);
    exit_section_(b, m, CHAR_CLASS_EXCLAMATION, r);
    return r;
  }

  /* ********************************************************** */
  // CHARCLASS_LETTER
  public static boolean char_class_letter(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "char_class_letter")) return false;
    if (!nextTokenIs(b, CHARCLASS_LETTER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, CHARCLASS_LETTER);
    exit_section_(b, m, CHAR_CLASS_LETTER, r);
    return r;
  }

  /* ********************************************************** */
  // L_BRACKET char_class_exclamation? char_class_letter+ R_BRACKET
  public static boolean char_class_pattern(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "char_class_pattern")) return false;
    if (!nextTokenIs(b, L_BRACKET)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, L_BRACKET);
    r = r && char_class_pattern_1(b, l + 1);
    r = r && char_class_pattern_2(b, l + 1);
    r = r && consumeToken(b, R_BRACKET);
    exit_section_(b, m, CHAR_CLASS_PATTERN, r);
    return r;
  }

  // char_class_exclamation?
  private static boolean char_class_pattern_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "char_class_pattern_1")) return false;
    char_class_exclamation(b, l + 1);
    return true;
  }

  // char_class_letter+
  private static boolean char_class_pattern_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "char_class_pattern_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = char_class_letter(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!char_class_letter(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "char_class_pattern_2", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // elemental_pattern (elemental_pattern)+
  public static boolean concatenated_pattern(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "concatenated_pattern")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _COLLAPSE_, CONCATENATED_PATTERN, "<concatenated pattern>");
    r = elemental_pattern(b, l + 1);
    r = r && concatenated_pattern_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // (elemental_pattern)+
  private static boolean concatenated_pattern_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "concatenated_pattern_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = concatenated_pattern_1_0(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!concatenated_pattern_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "concatenated_pattern_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // (elemental_pattern)
  private static boolean concatenated_pattern_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "concatenated_pattern_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = elemental_pattern(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // DOUBLE_ASTERISK
  public static boolean double_asterisk_pattern(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "double_asterisk_pattern")) return false;
    if (!nextTokenIs(b, DOUBLE_ASTERISK)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DOUBLE_ASTERISK);
    exit_section_(b, m, DOUBLE_ASTERISK_PATTERN, r);
    return r;
  }

  /* ********************************************************** */
  // root_declaration* section_wrap*
  static boolean editorConfigFile(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "editorConfigFile")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = editorConfigFile_0(b, l + 1);
    r = r && editorConfigFile_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // root_declaration*
  private static boolean editorConfigFile_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "editorConfigFile_0")) return false;
    while (true) {
      int c = current_position_(b);
      if (!root_declaration(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "editorConfigFile_0", c)) break;
    }
    return true;
  }

  // section_wrap*
  private static boolean editorConfigFile_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "editorConfigFile_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!section_wrap(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "editorConfigFile_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // enumeration_pattern | flat_pattern | asterisk_pattern | double_asterisk_pattern | question_pattern | char_class_pattern
  static boolean elemental_pattern(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "elemental_pattern")) return false;
    boolean r;
    r = enumeration_pattern(b, l + 1);
    if (!r) r = flat_pattern(b, l + 1);
    if (!r) r = asterisk_pattern(b, l + 1);
    if (!r) r = double_asterisk_pattern(b, l + 1);
    if (!r) r = question_pattern(b, l + 1);
    if (!r) r = char_class_pattern(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // L_CURLY (pattern_aux (COMMA pattern_aux)*)? R_CURLY
  public static boolean enumeration_pattern(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enumeration_pattern")) return false;
    if (!nextTokenIs(b, L_CURLY)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, L_CURLY);
    r = r && enumeration_pattern_1(b, l + 1);
    r = r && consumeToken(b, R_CURLY);
    exit_section_(b, m, ENUMERATION_PATTERN, r);
    return r;
  }

  // (pattern_aux (COMMA pattern_aux)*)?
  private static boolean enumeration_pattern_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enumeration_pattern_1")) return false;
    enumeration_pattern_1_0(b, l + 1);
    return true;
  }

  // pattern_aux (COMMA pattern_aux)*
  private static boolean enumeration_pattern_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enumeration_pattern_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = pattern_aux(b, l + 1);
    r = r && enumeration_pattern_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA pattern_aux)*
  private static boolean enumeration_pattern_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enumeration_pattern_1_0_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!enumeration_pattern_1_0_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "enumeration_pattern_1_0_1", c)) break;
    }
    return true;
  }

  // COMMA pattern_aux
  private static boolean enumeration_pattern_1_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "enumeration_pattern_1_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && pattern_aux(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER
  public static boolean flat_option_key(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "flat_option_key")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    exit_section_(b, m, FLAT_OPTION_KEY, r);
    return r;
  }

  /* ********************************************************** */
  // (PATTERN_IDENTIFIER | PATTERN_WHITE_SPACE)+
  public static boolean flat_pattern(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "flat_pattern")) return false;
    if (!nextTokenIs(b, "<flat pattern>", PATTERN_IDENTIFIER, PATTERN_WHITE_SPACE)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, FLAT_PATTERN, "<flat pattern>");
    r = flat_pattern_0(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!flat_pattern_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "flat_pattern", c)) break;
    }
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // PATTERN_IDENTIFIER | PATTERN_WHITE_SPACE
  private static boolean flat_pattern_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "flat_pattern_0")) return false;
    boolean r;
    r = consumeToken(b, PATTERN_IDENTIFIER);
    if (!r) r = consumeToken(b, PATTERN_WHITE_SPACE);
    return r;
  }

  /* ********************************************************** */
  // L_BRACKET pattern_aux? R_BRACKET
  public static boolean header(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "header")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, HEADER, "<header>");
    r = consumeToken(b, L_BRACKET);
    p = r; // pin = 1
    r = r && report_error_(b, header_1(b, l + 1));
    r = p && consumeToken(b, R_BRACKET) && r;
    exit_section_(b, l, m, r, p, EditorConfigParserBase::not_next_entry);
    return r || p;
  }

  // pattern_aux?
  private static boolean header_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "header_1")) return false;
    pattern_aux(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // !header
  static boolean not_header(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "not_header")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !header(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // !(header | option | root_declaration)
  static boolean not_next_entry(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "not_next_entry")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !not_next_entry_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // header | option | root_declaration
  private static boolean not_next_entry_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "not_next_entry_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = header(b, l + 1);
    if (!r) r = option(b, l + 1);
    if (!r) r = root_declaration(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // option_with_raw_value | simple_option
  public static boolean option(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, OPTION, "<option>");
    r = option_with_raw_value(b, l + 1);
    if (!r) r = simple_option(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // qualified_option_key | flat_option_key
  static boolean option_key(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_key")) return false;
    if (!nextTokenIs(b, "", DOT, IDENTIFIER)) return false;
    boolean r;
    r = qualified_option_key(b, l + 1);
    if (!r) r = flat_option_key(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // option_value_pair | option_value_standalone_list | option_value_standalone_identifier
  static boolean option_value(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_value")) return false;
    if (!nextTokenIs(b, "", COMMA, IDENTIFIER)) return false;
    boolean r;
    r = option_value_pair(b, l + 1);
    if (!r) r = option_value_standalone_list(b, l + 1);
    if (!r) r = option_value_standalone_identifier(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER
  public static boolean option_value_identifier(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_value_identifier")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    exit_section_(b, m, OPTION_VALUE_IDENTIFIER, r);
    return r;
  }

  /* ********************************************************** */
  // COMMA* option_value_identifier (COMMA option_value_identifier !(DOT | SEPARATOR) | COMMA)+
  public static boolean option_value_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_value_list")) return false;
    if (!nextTokenIs(b, "<option value list>", COMMA, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, OPTION_VALUE_LIST, "<option value list>");
    r = option_value_list_0(b, l + 1);
    r = r && option_value_identifier(b, l + 1);
    r = r && option_value_list_2(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // COMMA*
  private static boolean option_value_list_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_value_list_0")) return false;
    while (true) {
      int c = current_position_(b);
      if (!consumeToken(b, COMMA)) break;
      if (!empty_element_parsed_guard_(b, "option_value_list_0", c)) break;
    }
    return true;
  }

  // (COMMA option_value_identifier !(DOT | SEPARATOR) | COMMA)+
  private static boolean option_value_list_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_value_list_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = option_value_list_2_0(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!option_value_list_2_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "option_value_list_2", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // COMMA option_value_identifier !(DOT | SEPARATOR) | COMMA
  private static boolean option_value_list_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_value_list_2_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = option_value_list_2_0_0(b, l + 1);
    if (!r) r = consumeToken(b, COMMA);
    exit_section_(b, m, null, r);
    return r;
  }

  // COMMA option_value_identifier !(DOT | SEPARATOR)
  private static boolean option_value_list_2_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_value_list_2_0_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && option_value_identifier(b, l + 1);
    r = r && option_value_list_2_0_0_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // !(DOT | SEPARATOR)
  private static boolean option_value_list_2_0_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_value_list_2_0_0_2")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NOT_);
    r = !option_value_list_2_0_0_2_0(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // DOT | SEPARATOR
  private static boolean option_value_list_2_0_0_2_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_value_list_2_0_0_2_0")) return false;
    boolean r;
    r = consumeToken(b, DOT);
    if (!r) r = consumeToken(b, SEPARATOR);
    return r;
  }

  /* ********************************************************** */
  // (option_value_list | option_value_identifier) COLON (option_value_list | option_value_identifier) <<followedByNewLineOrEndOfFile>>
  public static boolean option_value_pair(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_value_pair")) return false;
    if (!nextTokenIs(b, "<option value pair>", COMMA, IDENTIFIER)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, OPTION_VALUE_PAIR, "<option value pair>");
    r = option_value_pair_0(b, l + 1);
    r = r && consumeToken(b, COLON);
    p = r; // pin = 2
    r = r && report_error_(b, option_value_pair_2(b, l + 1));
    r = p && followedByNewLineOrEndOfFile(b, l + 1) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // option_value_list | option_value_identifier
  private static boolean option_value_pair_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_value_pair_0")) return false;
    boolean r;
    r = option_value_list(b, l + 1);
    if (!r) r = option_value_identifier(b, l + 1);
    return r;
  }

  // option_value_list | option_value_identifier
  private static boolean option_value_pair_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_value_pair_2")) return false;
    boolean r;
    r = option_value_list(b, l + 1);
    if (!r) r = option_value_identifier(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // option_value_identifier <<followedByNewLineOrEndOfFile>>
  static boolean option_value_standalone_identifier(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_value_standalone_identifier")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = option_value_identifier(b, l + 1);
    r = r && followedByNewLineOrEndOfFile(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // option_value_list <<followedByNewLineOrEndOfFile>>
  static boolean option_value_standalone_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_value_standalone_list")) return false;
    if (!nextTokenIs(b, "", COMMA, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = option_value_list(b, l + 1);
    r = r && followedByNewLineOrEndOfFile(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // <<isOptionWithRawValueKeyAhead>> option_key SEPARATOR <<rawOptionValue>>
  static boolean option_with_raw_value(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_with_raw_value")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = isOptionWithRawValueKeyAhead(b, l + 1);
    r = r && option_key(b, l + 1);
    r = r && consumeToken(b, SEPARATOR);
    r = r && rawOptionValue(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // concatenated_pattern | elemental_pattern
  static boolean pattern_aux(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_aux")) return false;
    boolean r;
    r = concatenated_pattern(b, l + 1);
    if (!r) r = elemental_pattern(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER
  public static boolean qualified_key_part(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "qualified_key_part")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    exit_section_(b, m, QUALIFIED_KEY_PART, r);
    return r;
  }

  /* ********************************************************** */
  // DOT* (qualified_key_part DOT+)+ qualified_key_part? | (DOT+ qualified_key_part)
  public static boolean qualified_option_key(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "qualified_option_key")) return false;
    if (!nextTokenIs(b, "<qualified option key>", DOT, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, QUALIFIED_OPTION_KEY, "<qualified option key>");
    r = qualified_option_key_0(b, l + 1);
    if (!r) r = qualified_option_key_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // DOT* (qualified_key_part DOT+)+ qualified_key_part?
  private static boolean qualified_option_key_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "qualified_option_key_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = qualified_option_key_0_0(b, l + 1);
    r = r && qualified_option_key_0_1(b, l + 1);
    r = r && qualified_option_key_0_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // DOT*
  private static boolean qualified_option_key_0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "qualified_option_key_0_0")) return false;
    while (true) {
      int c = current_position_(b);
      if (!consumeToken(b, DOT)) break;
      if (!empty_element_parsed_guard_(b, "qualified_option_key_0_0", c)) break;
    }
    return true;
  }

  // (qualified_key_part DOT+)+
  private static boolean qualified_option_key_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "qualified_option_key_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = qualified_option_key_0_1_0(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!qualified_option_key_0_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "qualified_option_key_0_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // qualified_key_part DOT+
  private static boolean qualified_option_key_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "qualified_option_key_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = qualified_key_part(b, l + 1);
    r = r && qualified_option_key_0_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // DOT+
  private static boolean qualified_option_key_0_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "qualified_option_key_0_1_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DOT);
    while (r) {
      int c = current_position_(b);
      if (!consumeToken(b, DOT)) break;
      if (!empty_element_parsed_guard_(b, "qualified_option_key_0_1_0_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // qualified_key_part?
  private static boolean qualified_option_key_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "qualified_option_key_0_2")) return false;
    qualified_key_part(b, l + 1);
    return true;
  }

  // DOT+ qualified_key_part
  private static boolean qualified_option_key_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "qualified_option_key_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = qualified_option_key_1_0(b, l + 1);
    r = r && qualified_key_part(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // DOT+
  private static boolean qualified_option_key_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "qualified_option_key_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, DOT);
    while (r) {
      int c = current_position_(b);
      if (!consumeToken(b, DOT)) break;
      if (!empty_element_parsed_guard_(b, "qualified_option_key_1_0", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // QUESTION
  public static boolean question_pattern(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "question_pattern")) return false;
    if (!nextTokenIs(b, QUESTION)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, QUESTION);
    exit_section_(b, m, QUESTION_PATTERN, r);
    return r;
  }

  /* ********************************************************** */
  public static boolean raw_option_value(PsiBuilder b, int l) {
    Marker m = enter_section_(b);
    exit_section_(b, m, RAW_OPTION_VALUE, true);
    return true;
  }

  /* ********************************************************** */
  // root_declaration_key SEPARATOR root_declaration_list (COLON root_declaration_list)?
  public static boolean root_declaration(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "root_declaration")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, ROOT_DECLARATION, "<root declaration>");
    r = root_declaration_key(b, l + 1);
    r = r && consumeToken(b, SEPARATOR);
    p = r; // pin = 2
    r = r && report_error_(b, root_declaration_list(b, l + 1));
    r = p && root_declaration_3(b, l + 1) && r;
    exit_section_(b, l, m, r, p, EditorConfigParserBase::not_next_entry);
    return r || p;
  }

  // (COLON root_declaration_list)?
  private static boolean root_declaration_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "root_declaration_3")) return false;
    root_declaration_3_0(b, l + 1);
    return true;
  }

  // COLON root_declaration_list
  private static boolean root_declaration_3_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "root_declaration_3_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COLON);
    r = r && root_declaration_list(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER
  public static boolean root_declaration_key(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "root_declaration_key")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    exit_section_(b, m, ROOT_DECLARATION_KEY, r);
    return r;
  }

  /* ********************************************************** */
  // root_declaration_value (COMMA root_declaration_value)*
  static boolean root_declaration_list(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "root_declaration_list")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = root_declaration_value(b, l + 1);
    r = r && root_declaration_list_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA root_declaration_value)*
  private static boolean root_declaration_list_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "root_declaration_list_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!root_declaration_list_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "root_declaration_list_1", c)) break;
    }
    return true;
  }

  // COMMA root_declaration_value
  private static boolean root_declaration_list_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "root_declaration_list_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && root_declaration_value(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // IDENTIFIER
  public static boolean root_declaration_value(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "root_declaration_value")) return false;
    if (!nextTokenIs(b, IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, IDENTIFIER);
    exit_section_(b, m, ROOT_DECLARATION_VALUE, r);
    return r;
  }

  /* ********************************************************** */
  // header option*
  public static boolean section(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "section")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, SECTION, "<section>");
    r = header(b, l + 1);
    p = r; // pin = 1
    r = r && section_1(b, l + 1);
    exit_section_(b, l, m, r, p, EditorConfigParserBase::not_header);
    return r || p;
  }

  // option*
  private static boolean section_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "section_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!option(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "section_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // section <<unbindComments>>
  static boolean section_wrap(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "section_wrap")) return false;
    if (!nextTokenIs(b, L_BRACKET)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = section(b, l + 1);
    r = r && unbindComments(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // option_key (SEPARATOR option_value?)?
  static boolean simple_option(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_option")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = option_key(b, l + 1);
    p = r; // pin = 1
    r = r && simple_option_1(b, l + 1);
    exit_section_(b, l, m, r, p, EditorConfigParserBase::not_next_entry);
    return r || p;
  }

  // (SEPARATOR option_value?)?
  private static boolean simple_option_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_option_1")) return false;
    simple_option_1_0(b, l + 1);
    return true;
  }

  // SEPARATOR option_value?
  private static boolean simple_option_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_option_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SEPARATOR);
    r = r && simple_option_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // option_value?
  private static boolean simple_option_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "simple_option_1_0_1")) return false;
    option_value(b, l + 1);
    return true;
  }

}
