package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiParser;
import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * Parser for Groovy script files
 *
 * @author Ilya.Sergey
 */
public class GroovyParser implements PsiParser {

  @NotNull
  public ASTNode parse(IElementType root, PsiBuilder builder) {

    PsiBuilder.Marker rootMarker = builder.mark();

    while (!builder.eof()) {
      builder.advanceLexer();
    }

    rootMarker.done(root);
    return builder.getTreeBuilt();

  }
}