// This is a generated file. Not intended for manual editing.
package ru.adelf.idea.dotenv.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import ru.adelf.idea.dotenv.psi.impl.*;

public interface DotEnvTypes {

  IElementType COMMENT = new DotEnvElementType("COMMENT");
  IElementType EMPTY_LINE = new DotEnvElementType("EMPTY_LINE");
  IElementType PROPERTY = new DotEnvElementType("PROPERTY");

  IElementType LINE_COMMENT = new DotEnvTokenType("LINE_COMMENT");
  IElementType SPACE = new DotEnvTokenType("SPACE");
  IElementType VALUE = new DotEnvTokenType("VALUE");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
       if (type == COMMENT) {
        return new DotEnvCommentImpl(node);
      }
      else if (type == EMPTY_LINE) {
        return new DotEnvEmptyLineImpl(node);
      }
      else if (type == PROPERTY) {
        return new DotEnvPropertyImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
