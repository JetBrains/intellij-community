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
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.Modifiers;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration.Declaration;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members.ClassMember;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members.EnumConstant;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members.InterfaceMember;
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
  public static boolean parseTypeDefinition(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker tdMarker = builder.mark();
    Modifiers.parse(builder, parser);

    final IElementType tdType = parseAfterModifiers(builder, parser);
    if (tdType == WRONGWAY) {
      tdMarker.rollbackTo();
      return false;
    }

    tdMarker.done(tdType);
    return true;
  }

  public static IElementType parseAfterModifiers(PsiBuilder builder, GroovyParser parser) {
    if (builder.getTokenType() == kCLASS && parseClass(builder, parser)) {
      return CLASS_DEFINITION;
    }

    if (builder.getTokenType() == kINTERFACE && parseInterface(builder, parser)) {
      return INTERFACE_DEFINITION;
    }

    if (builder.getTokenType() == kENUM && parseEnum(builder, parser)) {
      return ENUM_DEFINITION;
    }

    if (builder.getTokenType() == mAT && parseAnnotationType(builder, parser)) {
      return ANNOTATION_DEFINITION;
    }

    return WRONGWAY;
  }

  private static boolean parseInterface(PsiBuilder builder, GroovyParser parser) {
    if (!ParserUtils.getToken(builder, kINTERFACE)) {
      return false;
    }

    if (builder.getTokenType() != mIDENT) {
      builder.error(GroovyBundle.message("identifier.expected"));
      return false;
    }

    String name = builder.getTokenText();
    builder.advanceLexer();

    ParserUtils.getToken(builder, mNLS);

    TypeParameters.parse(builder);

    ReferenceElement.parseReferenceList(builder, kEXTENDS, EXTENDS_CLAUSE);
    ParserUtils.getToken(builder, mNLS);

    ReferenceElement.parseReferenceList(builder, kIMPLEMENTS, IMPLEMENTS_CLAUSE);
    ParserUtils.getToken(builder, mNLS);

    if (!parseInterfaceBlock(builder, name, parser)) {
      builder.error(GroovyBundle.message("interface.body.expected"));
    }

    return true;
  }

  private static boolean parseClass(PsiBuilder builder, GroovyParser parser) {
    if (!ParserUtils.getToken(builder, kCLASS)) {
      return false;
    }

    if (builder.getTokenType() != mIDENT) {
      builder.error(GroovyBundle.message("identifier.expected"));
      return false;
    }

    String name = builder.getTokenText();
    builder.advanceLexer();

    ParserUtils.getToken(builder, mNLS);

    TypeParameters.parse(builder);

    ParserUtils.getToken(builder, mNLS);

    if (builder.getTokenType() == kEXTENDS) {
      ReferenceElement.parseReferenceList(builder, kEXTENDS, EXTENDS_CLAUSE);
      ParserUtils.getToken(builder, mNLS);
    }

    if (builder.getTokenType() == kIMPLEMENTS) {
      ReferenceElement.parseReferenceList(builder, kIMPLEMENTS, IMPLEMENTS_CLAUSE);
    }

    ParserUtils.getToken(builder, mNLS);

    if (builder.getTokenType() != mLCURLY) {
      builder.error(GroovyBundle.message("lcurly.expected"));
      return true;
    }

    parseClassBody(builder, name, parser);

    return true;
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

    if (builder.getTokenType() == kEXTENDS) {
      ReferenceElement.parseReferenceList(builder, kEXTENDS, EXTENDS_CLAUSE);
      ParserUtils.getToken(builder, mNLS);
    }

    if (builder.getTokenType() == kIMPLEMENTS) {
      ReferenceElement.parseReferenceList(builder, kIMPLEMENTS, IMPLEMENTS_CLAUSE);
    }

    Separators.parse(builder);

    parseEnumBlock(builder, name, parser);

    return true;
  }

  private static boolean parseAnnotationType(PsiBuilder builder, GroovyParser parser) {
    if (!ParserUtils.getToken(builder, mAT)) {
      return false;
    }

    if (!ParserUtils.getToken(builder, kINTERFACE)) {
      return false;
    }

    if (!ParserUtils.getToken(builder, mIDENT)) {
      builder.error(GroovyBundle.message("annotation.definition.qualified.name.expected"));
      return false;
    }

    PsiBuilder.Marker abMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mLCURLY, GroovyBundle.message("lcurly.expected"))) {
      abMarker.rollbackTo();
      return false;
    }

    Separators.parse(builder);

    while (!builder.eof() && builder.getTokenType() != mRCURLY) {
      if (!parseAnnotationMember(builder, parser)) builder.advanceLexer();
      if (builder.getTokenType() == mRCURLY) break;
      if (!Separators.parse(builder)) {
        builder.error(GroovyBundle.message("separator.or.rcurly.expected"));
      }
    }

    ParserUtils.getToken(builder, mRCURLY, GroovyBundle.message("rcurly.expected"));

    abMarker.done(CLASS_BODY);
    return true;
  }

  private static boolean parseAnnotationMember(PsiBuilder builder, GroovyParser parser) {
    //type definition
    PsiBuilder.Marker typeDeclStartMarker = builder.mark();

    if (parseTypeDefinition(builder, parser)) {
      typeDeclStartMarker.drop();
      return true;
    }

    typeDeclStartMarker.rollbackTo();

    PsiBuilder.Marker declMarker = builder.mark();

    if (Declaration.parse(builder, true, true, parser)) {
      declMarker.drop();
      return true;
    }

    declMarker.rollbackTo();
    return false;
  }

  public static boolean parseClassBody(PsiBuilder builder, @Nullable String className, GroovyParser parser) {
    //allow errors
    PsiBuilder.Marker cbMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mLCURLY)) {
      builder.error(GroovyBundle.message("lcurly.expected"));
      cbMarker.rollbackTo();
      return false;
    }

    parseMembers(builder, className, parser);

    ParserUtils.getToken(builder, mRCURLY, GroovyBundle.message("rcurly.expected"));

    cbMarker.done(CLASS_BODY);
    return true;
  }

  private static void parseMembers(PsiBuilder builder, String className, GroovyParser parser) {
    Separators.parse(builder);

    while (!builder.eof() && builder.getTokenType() != mRCURLY) {
      if (!ClassMember.parse(builder, className, parser)) {
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

  private static boolean parseEnumBlock(PsiBuilder builder, @Nullable String enumName, GroovyParser parser) {
    //see also InterfaceBlock, EnumBlock, AnnotationBlock
    PsiBuilder.Marker ebMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mLCURLY)) {
      ebMarker.rollbackTo();
      return false;
    }

    Separators.parse(builder);

    if (parseEnumConstantStart(builder, parser)) {
      EnumConstant.parseConstantList(builder, parser);
    }

    parseMembers(builder, enumName, parser);

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

  private static boolean parseInterfaceBlock(PsiBuilder builder, @Nullable String interfaceName, GroovyParser parser) {
    //see also InterfaceBlock, EnumBlock, AnnotationBlock
    PsiBuilder.Marker ibMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mLCURLY)) {
      ibMarker.rollbackTo();
      return false;
    }

    Separators.parse(builder);

    while (!builder.eof() && builder.getTokenType() != mRCURLY) {
      if (!InterfaceMember.parse(builder, interfaceName, parser)) builder.advanceLexer();
      if (builder.getTokenType() == mRCURLY) break;
      if (!Separators.parse(builder)) {
        builder.error(GroovyBundle.message("separator.or.rcurly.expected"));
      }
    }

    ParserUtils.getToken(builder, mRCURLY, GroovyBundle.message("rcurly.expected"));

    ibMarker.done(CLASS_BODY);
    return true;
  }
}
