// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static org.editorconfig.language.psi.EditorConfigElementTypes.*;
import static org.editorconfig.language.parser.EditorConfigParserUtil.*;
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
    b = adapt_builder_(t, b, this, null);
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
  // L_BRACKET char_class_exclamation? char_class_letter+ R_BRACKET
  public static boolean char_class(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "char_class")) return false;
    if (!nextTokenIs(b, L_BRACKET)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, L_BRACKET);
    r = r && char_class_1(b, l + 1);
    r = r && char_class_2(b, l + 1);
    r = r && consumeToken(b, R_BRACKET);
    exit_section_(b, m, CHAR_CLASS, r);
    return r;
  }

  // char_class_exclamation?
  private static boolean char_class_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "char_class_1")) return false;
    char_class_exclamation(b, l + 1);
    return true;
  }

  // char_class_letter+
  private static boolean char_class_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "char_class_2")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = char_class_letter(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!char_class_letter(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "char_class_2", c)) break;
    }
    exit_section_(b, m, null, r);
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
  // PATTERN_IDENTIFIER
  public static boolean flat_pattern(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "flat_pattern")) return false;
    if (!nextTokenIs(b, PATTERN_IDENTIFIER)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, PATTERN_IDENTIFIER);
    exit_section_(b, m, FLAT_PATTERN, r);
    return r;
  }

  /* ********************************************************** */
  // L_BRACKET (pattern | pattern_enumeration)* R_BRACKET
  public static boolean header(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "header")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, HEADER, "<header>");
    r = consumeToken(b, L_BRACKET);
    p = r; // pin = 1
    r = r && report_error_(b, header_1(b, l + 1));
    r = p && consumeToken(b, R_BRACKET) && r;
    exit_section_(b, l, m, r, p, not_next_entry_parser_);
    return r || p;
  }

  // (pattern | pattern_enumeration)*
  private static boolean header_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "header_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!header_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "header_1", c)) break;
    }
    return true;
  }

  // pattern | pattern_enumeration
  private static boolean header_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "header_1_0")) return false;
    boolean r;
    r = pattern(b, l + 1);
    if (!r) r = pattern_enumeration(b, l + 1);
    return r;
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
  // option_key (SEPARATOR option_value?)?
  public static boolean option(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option")) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_, OPTION, "<option>");
    r = option_key(b, l + 1);
    p = r; // pin = 1
    r = r && option_1(b, l + 1);
    exit_section_(b, l, m, r, p, not_next_entry_parser_);
    return r || p;
  }

  // (SEPARATOR option_value?)?
  private static boolean option_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_1")) return false;
    option_1_0(b, l + 1);
    return true;
  }

  // SEPARATOR option_value?
  private static boolean option_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SEPARATOR);
    r = r && option_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // option_value?
  private static boolean option_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "option_1_0_1")) return false;
    option_value(b, l + 1);
    return true;
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
  // (flat_pattern | asterisk_pattern | double_asterisk_pattern | question_pattern | char_class)+
  public static boolean pattern(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, PATTERN, "<pattern>");
    r = pattern_0(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!pattern_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "pattern", c)) break;
    }
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // flat_pattern | asterisk_pattern | double_asterisk_pattern | question_pattern | char_class
  private static boolean pattern_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_0")) return false;
    boolean r;
    r = flat_pattern(b, l + 1);
    if (!r) r = asterisk_pattern(b, l + 1);
    if (!r) r = double_asterisk_pattern(b, l + 1);
    if (!r) r = question_pattern(b, l + 1);
    if (!r) r = char_class(b, l + 1);
    return r;
  }

  /* ********************************************************** */
  // L_CURLY (pattern (COMMA pattern)*)? R_CURLY
  public static boolean pattern_enumeration(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_enumeration")) return false;
    if (!nextTokenIs(b, L_CURLY)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, L_CURLY);
    r = r && pattern_enumeration_1(b, l + 1);
    r = r && consumeToken(b, R_CURLY);
    exit_section_(b, m, PATTERN_ENUMERATION, r);
    return r;
  }

  // (pattern (COMMA pattern)*)?
  private static boolean pattern_enumeration_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_enumeration_1")) return false;
    pattern_enumeration_1_0(b, l + 1);
    return true;
  }

  // pattern (COMMA pattern)*
  private static boolean pattern_enumeration_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_enumeration_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = pattern(b, l + 1);
    r = r && pattern_enumeration_1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // (COMMA pattern)*
  private static boolean pattern_enumeration_1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_enumeration_1_0_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!pattern_enumeration_1_0_1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "pattern_enumeration_1_0_1", c)) break;
    }
    return true;
  }

  // COMMA pattern
  private static boolean pattern_enumeration_1_0_1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "pattern_enumeration_1_0_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, COMMA);
    r = r && pattern(b, l + 1);
    exit_section_(b, m, null, r);
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
    exit_section_(b, l, m, r, p, not_next_entry_parser_);
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
    exit_section_(b, l, m, r, p, not_header_parser_);
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

  static final Parser not_header_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return not_header(b, l + 1);
    }
  };
  static final Parser not_next_entry_parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return not_next_entry(b, l + 1);
    }
  };
}
