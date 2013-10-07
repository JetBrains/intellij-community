/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members.EnumConstant;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeParameters;

import static org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils.getToken;

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

    getToken(builder, mNLS);
    TypeParameters.parse(builder);

    getToken(builder, mNLS);
    ReferenceElement.parseReferenceList(builder, kEXTENDS, EXTENDS_CLAUSE, type);

    getToken(builder, mNLS);
    ReferenceElement.parseReferenceList(builder, kIMPLEMENTS, IMPLEMENTS_CLAUSE, type);

    getToken(builder, mNLS);
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

    if (!getToken(builder, mLCURLY)) {
      builder.error(GroovyBundle.message("lcurly.expected"));
      cbMarker.rollbackTo();
      return WRONGWAY;
    }

    parseMembers(builder, className, parser, isInAnnotation);

    getToken(builder, mRCURLY, GroovyBundle.message("rcurly.expected"));

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

    if (!getToken(builder, mLCURLY)) {
      ebMarker.rollbackTo();
      return WRONGWAY;
    }

    getToken(builder, mNLS);

    EnumConstant.parseConstantList(builder, parser);

    parseMembers(builder, enumName, parser, false);

    getToken(builder, mRCURLY, GroovyBundle.message("rcurly.expected"));

    ebMarker.done(ENUM_BODY);
    return ENUM_BODY;
  }
}
