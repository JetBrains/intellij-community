package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions;

import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration.Declaration;
import com.intellij.psi.tree.IElementType;
import com.intellij.lang.PsiBuilder;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 20.03.2007
 */
public class ClassMember implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    IElementType decl = Declaration.parse(builder); 
    if (!tWRONG_SET.contains(decl)) return decl;

//    todo: add clauses

    return WRONGWAY;
  }
}
