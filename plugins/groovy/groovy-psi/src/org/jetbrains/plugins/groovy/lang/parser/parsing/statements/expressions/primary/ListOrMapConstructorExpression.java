// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.primary;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.ArgumentList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author ilyas
 */
public class ListOrMapConstructorExpression {

  public static IElementType parse(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker marker = builder.mark();
    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mLBRACK, GroovyBundle.message("lbrack.expected"))) {
      marker.drop();
      return GroovyElementTypes.WRONGWAY;
    }
    if (ParserUtils.getToken(builder, GroovyTokenTypes.mRBRACK)) {
      marker.done(GroovyElementTypes.LIST_OR_MAP);
      return GroovyElementTypes.LIST_OR_MAP;
    } else if (ParserUtils.getToken(builder, GroovyTokenTypes.mCOLON)) {
      ParserUtils.getToken(builder, GroovyTokenTypes.mRBRACK, GroovyBundle.message("rbrack.expected"));
    } else {
      ArgumentList.parseArgumentList(builder, GroovyTokenTypes.mRBRACK, parser);
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      ParserUtils.getToken(builder, GroovyTokenTypes.mRBRACK, GroovyBundle.message("rbrack.expected"));
    }

    marker.done(GroovyElementTypes.LIST_OR_MAP);
    return GroovyElementTypes.LIST_OR_MAP;
  }
}
