// This is a generated file. Not intended for manual editing.
package ru.adelf.idea.dotenv.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import ru.adelf.idea.dotenv.psi.impl.*;

public interface DotEnvTypes {

  IElementType KEY = new DotEnvElementType("KEY");
  IElementType PROPERTY = new DotEnvElementType("PROPERTY");
  IElementType VALUE = new DotEnvElementType("VALUE");

  IElementType COMMENT = new DotEnvTokenType("COMMENT");
  IElementType CRLF = new DotEnvTokenType("CRLF");
  IElementType EXPORT = new DotEnvTokenType("EXPORT");
  IElementType KEY_CHARS = new DotEnvTokenType("KEY_CHARS");
  IElementType QUOTE = new DotEnvTokenType("QUOTE");
  IElementType SEPARATOR = new DotEnvTokenType("SEPARATOR");
  IElementType VALUE_CHARS = new DotEnvTokenType("VALUE_CHARS");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == KEY) {
        return new DotEnvKeyImpl(node);
      }
      else if (type == PROPERTY) {
        return new DotEnvPropertyImpl(node);
      }
      else if (type == VALUE) {
        return new DotEnvValueImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
