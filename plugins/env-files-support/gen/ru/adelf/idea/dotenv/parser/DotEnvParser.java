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
    if (t == COMMENT) {
      r = comment(b, 0);
    }
    else if (t == EMPTY_LINE) {
      r = empty_line(b, 0);
    }
    else if (t == PROPERTY) {
      r = property(b, 0);
    }
    else {
      r = parse_root_(t, b, 0);
    }
    exit_section_(b, 0, m, t, r, true, TRUE_CONDITION);
  }

  protected boolean parse_root_(IElementType t, PsiBuilder b, int l) {
    return dotEnvFile(b, l + 1);
  }

  /* ********************************************************** */
  // LINE_COMMENT
  public static boolean comment(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "comment")) return false;
    if (!nextTokenIs(b, LINE_COMMENT)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, LINE_COMMENT);
    exit_section_(b, m, COMMENT, r);
    return r;
  }

  /* ********************************************************** */
  // (property|comment|empty_line)*
  static boolean dotEnvFile(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dotEnvFile")) return false;
    int c = current_position_(b);
    while (true) {
      if (!dotEnvFile_0(b, l + 1)) break;
      if (!empty_element_parsed_guard_(b, "dotEnvFile", c)) break;
      c = current_position_(b);
    }
    return true;
  }

  // property|comment|empty_line
  private static boolean dotEnvFile_0(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "dotEnvFile_0")) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = property(b, l + 1);
    if (!r) r = comment(b, l + 1);
    if (!r) r = empty_line(b, l + 1);
    exit_section_(b, m, null, r);
    return r;
  }

  /* ********************************************************** */
  // SPACE
  public static boolean empty_line(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "empty_line")) return false;
    if (!nextTokenIs(b, SPACE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, SPACE);
    exit_section_(b, m, EMPTY_LINE, r);
    return r;
  }

  /* ********************************************************** */
  // VALUE
  public static boolean property(PsiBuilder b, int l) {
    if (!recursion_guard_(b, l, "property")) return false;
    if (!nextTokenIs(b, VALUE)) return false;
    boolean r;
    Marker m = enter_section_(b);
    r = consumeToken(b, VALUE);
    exit_section_(b, m, PROPERTY, r);
    return r;
  }

}
