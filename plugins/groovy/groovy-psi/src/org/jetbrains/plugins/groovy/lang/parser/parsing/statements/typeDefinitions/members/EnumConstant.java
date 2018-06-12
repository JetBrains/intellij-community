// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.Modifiers;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions.arguments.ArgumentList;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.TypeDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 06.04.2007
 */
public class EnumConstant {
  private static boolean parseEnumConstant(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker ecMarker = builder.mark();

    Modifiers.parse(builder, parser, true);

    if (!ParserUtils.getToken(builder, GroovyTokenTypes.mIDENT)) {
      ecMarker.rollbackTo();
      return false;
    }

    if (GroovyTokenTypes.mLPAREN.equals(builder.getTokenType())) {
      PsiBuilder.Marker marker = builder.mark();
      ParserUtils.getToken(builder, GroovyTokenTypes.mLPAREN);
      ArgumentList.parseArgumentList(builder, GroovyTokenTypes.mRPAREN, parser);

      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      ParserUtils.getToken(builder, GroovyTokenTypes.mRPAREN, GroovyBundle.message("rparen.expected"));
      marker.done(GroovyElementTypes.ARGUMENTS);
    }

    if (builder.getTokenType() == GroovyTokenTypes.mLCURLY) {
      final PsiBuilder.Marker enumInitializer = builder.mark();
      TypeDefinition.parseBody(builder, null, parser, false);
      enumInitializer.done(GroovyElementTypes.ENUM_CONSTANT_INITIALIZER);
    }

    ecMarker.done(GroovyElementTypes.ENUM_CONSTANT);
    return true;

  }

  public static boolean parseConstantList(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker enumConstantsMarker = builder.mark();

    if (!parseEnumConstant(builder, parser)) {
      enumConstantsMarker.drop();
      return false;
    }

    while (ParserUtils.getToken(builder, GroovyTokenTypes.mCOMMA) ||
           ParserUtils.getToken(builder, GroovyTokenTypes.mNLS, GroovyTokenTypes.mCOMMA)) {
      PsiBuilder.Marker constMarker = builder.mark();
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      if (parseEnumConstant(builder, parser)) {
        constMarker.drop();
      }
      else {
        constMarker.rollbackTo(); // don't eat new line
      }
    }

    ParserUtils.getToken(builder, GroovyTokenTypes.mCOMMA);

    enumConstantsMarker.done(GroovyElementTypes.ENUM_CONSTANTS);

    return true;
  }
}
