/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members.EnumConstant;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeParameters;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.*;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kINTERFACE;
import static org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.kTRAIT;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 16.03.2007
 */

/*
 * TypeDefinition ::= ClassDefinition
 *                          | InterfaceDefinition
 *                          | EnumDefinition
 *                          | AnnotationDefinition 
 */

public class TypeDefinition implements GroovyElementTypes {
  public static IElementType parseAfterModifiers(PsiBuilder builder, GroovyParser parser) {
    if (builder.getTokenType() == kCLASS) {
      builder.advanceLexer();
      if (parseAfterKeyword(builder, parser, ClassType.CLASS)) {
        return CLASS_DEFINITION;
      }
    }

    if (builder.getTokenType() == kINTERFACE) {
      builder.advanceLexer();
      if (parseAfterKeyword(builder, parser, ClassType.INTERFACE)) {
        return INTERFACE_DEFINITION;
      }
    }

    if (builder.getTokenType() == kENUM) {
      builder.advanceLexer();
      if (parseAfterKeyword(builder, parser, ClassType.ENUM)) {
        return ENUM_DEFINITION;
      }
    }

    if (builder.getTokenType() == kTRAIT) {
      builder.advanceLexer();
      if (parseAfterKeyword(builder, parser, ClassType.TRAIT)) {
        return TRAIT_DEFINITION;
      }
    }

    if (builder.getTokenType() == mAT) {
      builder.advanceLexer();
      if (builder.getTokenType() == kINTERFACE) {
        builder.advanceLexer();
        if (parseAfterKeyword(builder, parser, ClassType.ANNOTATION)) {
          return ANNOTATION_DEFINITION;
        }
      }
    }

    return WRONGWAY;
  }

  private static boolean parseAfterKeyword(final PsiBuilder builder, final GroovyParser parser, final ClassType type) {
    if (builder.getTokenType() != mIDENT) {
      builder.error(GroovyBundle.message("identifier.expected"));
      return false;
    }

    final String name = builder.getTokenText();
    assert name != null;
    builder.advanceLexer();

    ParserUtils.getToken(builder, mNLS);
    TypeParameters.parse(builder);

    ParserUtils.getToken(builder, mNLS);
    ReferenceElement.parseReferenceList(builder, kEXTENDS, EXTENDS_CLAUSE, type);

    ParserUtils.getToken(builder, mNLS);
    ReferenceElement.parseReferenceList(builder, kIMPLEMENTS, IMPLEMENTS_CLAUSE, type);

    ParserUtils.getToken(builder, mNLS);
    if (builder.getTokenType() == mLCURLY) {
      if (type == ClassType.ENUM) {
        parseEnumBody(builder, name, parser);
      }
      else {
        parseBody(builder, name, parser, type == ClassType.ANNOTATION);
      }
    }
    else {
      builder.error(GroovyBundle.message("lcurly.expected"));
    }
    return true;
  }

  public static IElementType parseBody(@NotNull PsiBuilder builder,
                                       @Nullable String className,
                                       @NotNull GroovyParser parser,
                                       final boolean isInAnnotation) {
    //allow errors
    PsiBuilder.Marker cbMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mLCURLY)) {
      builder.error(GroovyBundle.message("lcurly.expected"));
      cbMarker.rollbackTo();
      return WRONGWAY;
    }

    parseMembers(builder, className, parser, isInAnnotation);

    ParserUtils.getToken(builder, mRCURLY, GroovyBundle.message("rcurly.expected"));

    cbMarker.done(CLASS_BODY);
    return CLASS_BODY;
  }

  private static void parseMembers(@NotNull PsiBuilder builder,
                                   @Nullable String className,
                                   @NotNull GroovyParser parser,
                                   final boolean isInAnnotation) {
    Separators.parse(builder);

    while (!builder.eof() && builder.getTokenType() != mRCURLY) {
      if (!parser.parseDeclaration(builder, true, isInAnnotation, className)) {
        builder.advanceLexer();
        builder.error(GroovyBundle.message("separator.or.rcurly.expected"));
      }
      if (builder.getTokenType() == mRCURLY) {
        break;
      }
      if (!Separators.parse(builder)) {
        builder.error(GroovyBundle.message("separator.or.rcurly.expected"));
      }
    }
  }

  private static IElementType parseEnumBody(@NotNull PsiBuilder builder,
                                            @NotNull String enumName,
                                            @NotNull GroovyParser parser) {
    PsiBuilder.Marker ebMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mLCURLY)) {
      ebMarker.rollbackTo();
      return WRONGWAY;
    }

    ParserUtils.getToken(builder, mNLS);

    if (EnumConstant.parseConstantList(builder, parser)) {
      if (!ParserUtils.lookAhead(builder, mRCURLY)) {
        ParserUtils.getToken(builder, TokenSet.create(mNLS, mSEMI), GroovyBundle.message("separator.or.rcurly.expected"));
      }
    }

    parseMembers(builder, enumName, parser, false);

    ParserUtils.getToken(builder, mRCURLY, GroovyBundle.message("rcurly.expected"));

    ebMarker.done(ENUM_BODY);
    return ENUM_BODY;
  }
}
