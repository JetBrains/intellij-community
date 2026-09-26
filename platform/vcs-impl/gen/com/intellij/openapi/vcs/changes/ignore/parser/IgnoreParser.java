// This is a generated file. Not intended for manual editing.
package com.intellij.openapi.vcs.changes.ignore.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import static com.intellij.openapi.vcs.changes.ignore.psi.IgnoreTypes.*;
import static com.intellij.lang.parser.GeneratedParserUtilBase.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiParser;
import com.intellij.lang.LightPsiParser;

@SuppressWarnings({"SimplifiableIfStatement", "UnusedAssignment"})
public class IgnoreParser implements PsiParser, LightPsiParser {

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
    return ignoreFile(b, l + 1);
  }

  public static final TokenSet[] EXTENDS_SETS_ = new TokenSet[] {
    create_token_set_(ENTRY, ENTRY_DIRECTORY, ENTRY_FILE),
  };

  /* ********************************************************** */
  // NEGATION ? SLASH ? <<list_macro value_>>
  public static boolean ENTRY(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ENTRY")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ENTRY, "<entry>");
    r = ENTRY_0(b, l + 1);
    r = r && ENTRY_1(b, l + 1);
    r = r && list_macro(b, l + 1, value__parser_);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // NEGATION ?
  private static boolean ENTRY_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ENTRY_0")) return false;
    NEGATION(b, l + 1);
    return true;
  }

  // SLASH ?
  private static boolean ENTRY_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ENTRY_1")) return false;
    consumeToken(b, SLASH);
    return true;
  }

  /* ********************************************************** */
  // NEGATION ? SLASH ? <<list_macro value_>> SLASH
  public static boolean ENTRY_DIRECTORY(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ENTRY_DIRECTORY")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ENTRY_DIRECTORY, "<entry>");
    r = ENTRY_DIRECTORY_0(b, l + 1);
    r = r && ENTRY_DIRECTORY_1(b, l + 1);
    r = r && list_macro(b, l + 1, value__parser_);
    r = r && consumeToken(b, SLASH);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // NEGATION ?
  private static boolean ENTRY_DIRECTORY_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ENTRY_DIRECTORY_0")) return false;
    NEGATION(b, l + 1);
    return true;
  }

  // SLASH ?
  private static boolean ENTRY_DIRECTORY_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ENTRY_DIRECTORY_1")) return false;
    consumeToken(b, SLASH);
    return true;
  }

  /* ********************************************************** */
  // NEGATION ? SLASH ? <<list_macro value_>>
  public static boolean ENTRY_FILE(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ENTRY_FILE")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, ENTRY_FILE, "<entry>");
    r = ENTRY_FILE_0(b, l + 1);
    r = r && ENTRY_FILE_1(b, l + 1);
    r = r && list_macro(b, l + 1, value__parser_);
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  // NEGATION ?
  private static boolean ENTRY_FILE_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ENTRY_FILE_0")) return false;
    NEGATION(b, l + 1);
    return true;
  }

  // SLASH ?
  private static boolean ENTRY_FILE_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ENTRY_FILE_1")) return false;
    consumeToken(b, SLASH);
    return true;
  }

  /* ********************************************************** */
  // "!"
  public static boolean NEGATION(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "NEGATION")) return false;
    boolean r;
    Marker m = enter_section_(b, l, _NONE_, NEGATION, "<negation>");
    r = consumeToken(b, "!");
    exit_section_(b, l, m, r, false, null);
    return r;
  }

  /* ********************************************************** */
  // SYNTAX_KEY CRLF * VALUE
  public static boolean SYNTAX(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "SYNTAX")) return false;
    if (!nextTokenIs(b, SYNTAX_KEY)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SYNTAX_KEY);
    r = r && SYNTAX_1(b, l + 1);
    r = r && consumeToken(b, VALUE);
    exit_section_(b, m, SYNTAX, r);
    return r;
  }

  // CRLF *
  private static boolean SYNTAX_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "SYNTAX_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!consumeToken(b, CRLF)) break;
      if (!empty_element_parsed_guard_(b, "SYNTAX_1", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // BRACKET_LEFT ( VALUE SLASH ? ) + BRACKET_RIGHT
  static boolean bvalue_(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bvalue_")) return false;
    if (!nextTokenIs(b, BRACKET_LEFT)) return false;
    boolean r, p;
    Marker m = enter_section_(b, l, _NONE_);
    r = consumeToken(b, BRACKET_LEFT);
    p = r; // pin = BRACKET_LEFT
    r = r && report_error_(b, bvalue__1(b, l + 1));
    r = p && consumeToken(b, BRACKET_RIGHT) && r;
    exit_section_(b, l, m, r, p, null);
    return r || p;
  }

  // ( VALUE SLASH ? ) +
  private static boolean bvalue__1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bvalue__1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = bvalue__1_0(b, l + 1);
    while (r) {
      int c = current_position_(b);
      if (!bvalue__1_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "bvalue__1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // VALUE SLASH ?
  private static boolean bvalue__1_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bvalue__1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, VALUE);
    r = r && bvalue__1_0_1(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  // SLASH ?
  private static boolean bvalue__1_0_1(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "bvalue__1_0_1")) return false;
    consumeToken(b, SLASH);
    return true;
  }

  /* ********************************************************** */
  // item_ *
  static boolean ignoreFile(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "ignoreFile")) return false;
    while (true) {
      int c = current_position_(b);
      if (!item_(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "ignoreFile", c)) break;
    }
    return true;
  }

  /* ********************************************************** */
  // HEADER | SECTION | COMMENT | SYNTAX | ENTRY_DIRECTORY | ENTRY_FILE | CRLF
  static boolean item_(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "item_")) return false;
    boolean r;
    r = consumeToken(b, HEADER);
    if (!r) r = consumeToken(b, SECTION);
    if (!r) r = consumeToken(b, COMMENT);
    if (!r) r = SYNTAX(b, l + 1);
    if (!r) r = ENTRY_DIRECTORY(b, l + 1);
    if (!r) r = ENTRY_FILE(b, l + 1);
    if (!r) r = consumeToken(b, CRLF);
    return r;
  }

  /* ********************************************************** */
  // <<p>> + (SLASH <<p>> +) *
  static boolean list_macro(PsiBuilder b, int l, Parser _p) {
    if (!recursion_guard_(b, l, "list_macro")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = list_macro_0(b, l + 1, _p);
    r = r && list_macro_1(b, l + 1, _p);
    exit_section_(b, m, null, r);
    return r;
  }

  // <<p>> +
  private static boolean list_macro_0(PsiBuilder b, int l, Parser _p) {
    if (!recursion_guard_(b, l, "list_macro_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = _p.parse(b, l);
    while (r) {
      int c = current_position_(b);
      if (!_p.parse(b, l)) break;
      if (!empty_element_parsed_guard_(b, "list_macro_0", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  // (SLASH <<p>> +) *
  private static boolean list_macro_1(PsiBuilder b, int l, Parser _p) {
    if (!recursion_guard_(b, l, "list_macro_1")) return false;
    while (true) {
      int c = current_position_(b);
      if (!list_macro_1_0(b, l + 1, _p)) break;
      if (!empty_element_parsed_guard_(b, "list_macro_1", c)) break;
    }
    return true;
  }

  // SLASH <<p>> +
  private static boolean list_macro_1_0(PsiBuilder b, int l, Parser _p) {
    if (!recursion_guard_(b, l, "list_macro_1_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SLASH);
    r = r && list_macro_1_0_1(b, l + 1, _p);
    exit_section_(b, m, null, r);
    return r;
  }

  // <<p>> +
  private static boolean list_macro_1_0_1(PsiBuilder b, int l, Parser _p) {
    if (!recursion_guard_(b, l, "list_macro_1_0_1")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = _p.parse(b, l);
    while (r) {
      int c = current_position_(b);
      if (!_p.parse(b, l)) break;
      if (!empty_element_parsed_guard_(b, "list_macro_1_0_1", c)) break;
    }
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // bvalue_ | VALUE
  static boolean value_(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "value_")) return false;
    if (!nextTokenIs(b, "", BRACKET_LEFT, VALUE)) return false;
    boolean r;
    r = bvalue_(b, l + 1);
    if (!r) r = consumeToken(b, VALUE);
    return r;
  }

  static final Parser value__parser_ = new Parser() {
    public boolean parse(PsiBuilder b, int l) {
      return value_(b, l + 1);
    }
  };
}
