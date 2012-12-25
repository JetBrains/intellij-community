/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members.EnumConstant;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeParameters;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

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
      if (parseClassAfterKeyword(builder, parser, false)) {
        return CLASS_DEFINITION;
      }
    }

    if (builder.getTokenType() == kINTERFACE) {
      builder.advanceLexer();
      if (parseClassAfterKeyword(builder, parser, false)) {
        return INTERFACE_DEFINITION;
      }
    }

    if (builder.getTokenType() == kENUM && parseEnum(builder, parser)) {
      return ENUM_DEFINITION;
    }

    if (builder.getTokenType() == mAT) {
      builder.advanceLexer();
      if (builder.getTokenType() == kINTERFACE) {
        builder.advanceLexer();
        if (parseClassAfterKeyword(builder, parser, true)) {
          return ANNOTATION_DEFINITION;
        }
      }
    }

    return WRONGWAY;
  }

  private static boolean parseClassAfterKeyword(PsiBuilder builder, GroovyParser parser, final boolean isInAnnotation) {
    if (builder.getTokenType() != mIDENT) {
      builder.error(GroovyBundle.message("identifier.expected"));
      return false;
    }

    String name = builder.getTokenText();
    builder.advanceLexer();

    ParserUtils.getToken(builder, mNLS);

    TypeParameters.parse(builder);

    ParserUtils.getToken(builder, mNLS);

    ReferenceElement.parseReferenceList(builder, kEXTENDS, EXTENDS_CLAUSE);
    ParserUtils.getToken(builder, mNLS);

    ReferenceElement.parseReferenceList(builder, kIMPLEMENTS, IMPLEMENTS_CLAUSE);
    ParserUtils.getToken(builder, mNLS);

    if (builder.getTokenType() == mLCURLY) {
      parseClassBody(builder, name, parser, isInAnnotation);
    }
    else {
      builder.error(GroovyBundle.message("lcurly.expected"));
    }
    return true;
  }

  public static boolean parseClassBody(PsiBuilder builder, @Nullable String className, GroovyParser parser, final boolean isInAnnotation) {
    //allow errors
    PsiBuilder.Marker cbMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mLCURLY)) {
      builder.error(GroovyBundle.message("lcurly.expected"));
      cbMarker.rollbackTo();
      return false;
    }

    parseMembers(builder, className, parser, isInAnnotation);

    ParserUtils.getToken(builder, mRCURLY, GroovyBundle.message("rcurly.expected"));

    cbMarker.done(CLASS_BODY);
    return true;
  }

  private static void parseMembers(PsiBuilder builder, String className, GroovyParser parser, final boolean isInAnnotation) {
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

  private static boolean parseEnum(PsiBuilder builder, GroovyParser parser) {
    if (!ParserUtils.getToken(builder, kENUM)) {
      return false;
    }

    if (builder.getTokenType() != mIDENT) {
      builder.error(GroovyBundle.message("identifier.expected"));
      return false;
    }

    String name = builder.getTokenText();
    builder.advanceLexer();

    ReferenceElement.parseReferenceList(builder, kEXTENDS, EXTENDS_CLAUSE);
    ParserUtils.getToken(builder, mNLS);

    ReferenceElement.parseReferenceList(builder, kIMPLEMENTS, IMPLEMENTS_CLAUSE);
    ParserUtils.getToken(builder, mNLS);


    parseEnumBlock(builder, name, parser);

    return true;
  }

  private static boolean parseEnumBlock(PsiBuilder builder, @Nullable String enumName, GroovyParser parser) {
    PsiBuilder.Marker ebMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mLCURLY)) {
      ebMarker.rollbackTo();
      return false;
    }

    ParserUtils.getToken(builder, mNLS);

    if (parseEnumConstantStart(builder, parser)) {
      EnumConstant.parseConstantList(builder, parser);
    }

    parseMembers(builder, enumName, parser, false);

    ParserUtils.getToken(builder, mRCURLY, GroovyBundle.message("rcurly.expected"));

    ebMarker.done(ENUM_BODY);
    return true;
  }

  private static boolean parseEnumConstantStart(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker checkMarker = builder.mark();

    boolean result = EnumConstant.parseEnumConstant(builder, parser) &&
                     (ParserUtils.getToken(builder, mCOMMA)
                      || ParserUtils.getToken(builder, mSEMI)
                      || ParserUtils.getToken(builder, mNLS)
                      || ParserUtils.getToken(builder, mRCURLY));

    checkMarker.rollbackTo();
    return result;
  }


}
