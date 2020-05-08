// This is a generated file. Not intended for manual editing.
package com.intellij.openapi.vcs.changes.ignore.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.PsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.vcs.changes.ignore.psi.impl.*;

public interface IgnoreTypes {

  IElementType ENTRY = new IgnoreElementType("ENTRY");
  IElementType ENTRY_DIRECTORY = new IgnoreElementType("ENTRY_DIRECTORY");
  IElementType ENTRY_FILE = new IgnoreElementType("ENTRY_FILE");
  IElementType NEGATION = new IgnoreElementType("NEGATION");
  IElementType SYNTAX = new IgnoreElementType("SYNTAX");

  IElementType BRACKET_LEFT = new IgnoreTokenType("BRACKET_LEFT");
  IElementType BRACKET_RIGHT = new IgnoreTokenType("BRACKET_RIGHT");
  IElementType COMMENT = new IgnoreTokenType("COMMENT");
  IElementType CRLF = new IgnoreTokenType("CRLF");
  IElementType HEADER = new IgnoreTokenType("HEADER");
  IElementType SECTION = new IgnoreTokenType("SECTION");
  IElementType SLASH = new IgnoreTokenType("/");
  IElementType SYNTAX_KEY = new IgnoreTokenType("syntax:");
  IElementType VALUE = new IgnoreTokenType("VALUE");

  class Factory {
    public static PsiElement createElement(ASTNode node) {
      IElementType type = node.getElementType();
      if (type == ENTRY) {
        return new IgnoreEntryImpl(node);
      }
      else if (type == ENTRY_DIRECTORY) {
        return new IgnoreEntryDirectoryImpl(node);
      }
      else if (type == ENTRY_FILE) {
        return new IgnoreEntryFileImpl(node);
      }
      else if (type == NEGATION) {
        return new IgnoreNegationImpl(node);
      }
      else if (type == SYNTAX) {
        return new IgnoreSyntaxImpl(node);
      }
      throw new AssertionError("Unknown element type: " + type);
    }
  }
}
