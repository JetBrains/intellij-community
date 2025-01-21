// This is a generated file. Not intended for manual editing.
package ru.adelf.idea.dotenv.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static ru.adelf.idea.dotenv.psi.DotEnvTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class DotEnvParser implements PsiParser, LightPsiParser {

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
    return dotEnvFile(b, l + 1);
  }

  /* ********************************************************** */
  // item_*
  static boolean dotEnvFile(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dotEnvFile")) return false;
    while (true) {
      int c = current_position_(b);
      if (!item_(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "dotEnvFile", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // EXPORT? property|COMMENT|CRLF
  static boolean item_(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "item_")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = item__0(b, l + 1);
    if (!r) r = consumeToken(b, COMMENT);
    if (!r) r = consumeToken(b, CRLF);
    exit_section_(b, m, null, r);
    return r;
  }

  // EXPORT? property
  private static boolean item__0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "item__0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = item__0_0(b, l + 1);
    r = r && property(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // EXPORT?
  private static boolean item__0_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "item__0_0")) return false;
    consumeToken(b, EXPORT);
    return true;
  }

  /* ********************************************************** */
  // KEY_CHARS
  public static boolean key(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "key")) return false;
    if (!nextTokenIs(b, KEY_CHARS)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, KEY_CHARS);
    exit_section_(b, m, KEY, r);
    return r;
  }

  /* ********************************************************** */
  // KEY_CHARS
  public static boolean nested_variable_key(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "nested_variable_key")) return false;
    if (!nextTokenIs(b, "<property key>", KEY_CHARS)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, NESTED_VARIABLE_KEY, "<property key>");
    r = consumeToken(b, KEY_CHARS);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // (key SEPARATOR value? COMMENT?) | key COMMENT?
  public static boolean property(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "property")) return false;
    if (!nextTokenIs(b, KEY_CHARS)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = property_0(b, l + 1);
    if (!r) r = property_1(b, l + 1);
    exit_section_(b, m, PROPERTY, r);
    return r;
  }

  // key SEPARATOR value? COMMENT?
  private static boolean property_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "property_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = key(b, l + 1);
    r = r && consumeToken(b, SEPARATOR);
    r = r && property_0_2(b, l + 1);
    r = r && property_0_3(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // value?
  private static boolean property_0_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "property_0_2")) return false;
    value(b, l + 1);
    return true;
  }

  // COMMENT?
  private static boolean property_0_3(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "property_0_3")) return false;
    consumeToken(b, COMMENT);
    return true;
  }

  // key COMMENT?
  private static boolean property_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "property_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = key(b, l + 1);
    r = r && property_1_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // COMMENT?
  private static boolean property_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "property_1_1")) return false;
    consumeToken(b, COMMENT);
    return true;
  }

  /* ********************************************************** */
  // (NESTED_VARIABLE_START nested_variable_key? NESTED_VARIABLE_END) | VALUE_CHARS
  static boolean quoted_value(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "quoted_value")) return false;
    if (!nextTokenIs(b, "", NESTED_VARIABLE_START, VALUE_CHARS)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = quoted_value_0(b, l + 1);
    if (!r) r = consumeToken(b, VALUE_CHARS);
    exit_section_(b, m, null, r);
    return r;
  }

  // NESTED_VARIABLE_START nested_variable_key? NESTED_VARIABLE_END
  private static boolean quoted_value_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "quoted_value_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, NESTED_VARIABLE_START);
    r = r && quoted_value_0_1(b, l + 1);
    r = r && consumeToken(b, NESTED_VARIABLE_END);
    exit_section_(b, m, null, r);
    return r;
  }

  // nested_variable_key?
  private static boolean quoted_value_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "quoted_value_0_1")) return false;
    nested_variable_key(b, l + 1);
    return true;
  }

  /* ********************************************************** */
  // VALUE_CHARS+ | QUOTE quoted_value* QUOTE?
  public static boolean value(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "value")) return false;
    if (!nextTokenIs(b, "<value>", QUOTE, VALUE_CHARS)) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, VALUE, "<value>");
    r = value_0(b, l + 1);
    if (!r) r = value_1(b, l + 1);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // VALUE_CHARS+
  private static boolean value_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "value_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, VALUE_CHARS);
    while (r) {
      int c = current_position_(b);
      if (!consumeToken(b, VALUE_CHARS)) break;
      if (!empty_element_parsed_guard_(b, "value_0", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // QUOTE quoted_value* QUOTE?
  private static boolean value_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "value_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, QUOTE);
    r = r && value_1_1(b, l + 1);
    r = r && value_1_2(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // quoted_value*
  private static boolean value_1_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "value_1_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!quoted_value(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "value_1_1", c)) break;
    }
    return true;
  }

  // QUOTE?
  private static boolean value_1_2(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "value_1_2")) return false;
    consumeToken(b, QUOTE);
    return true;
  }

}
