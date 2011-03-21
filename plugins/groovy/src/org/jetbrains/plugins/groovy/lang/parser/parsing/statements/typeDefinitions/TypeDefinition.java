/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration.Declaration;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.blocks.EnumBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.blocks.InterfaceBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.Modifiers;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members.ClassMember;
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

    String name;
    if (!mIDENT.equals(builder.getTokenType())) {
      builder.error(GroovyBundle.message("identifier.expected"));
      return false;
    } else {
      name = builder.getTokenText();
      builder.advanceLexer();
    }

    ParserUtils.getToken(builder, mNLS);

    TypeParameters.parse(builder);

    ExtendsClause.parse(builder);
    ParserUtils.getToken(builder, mNLS);

    ImplementsClause.parse(builder);
    ParserUtils.getToken(builder, mNLS);

    if (!InterfaceBlock.parse(builder, name, parser)) {
      builder.error(GroovyBundle.message("interface.body.expected"));
    }

    return true;
  }

  private static boolean parseClass(PsiBuilder builder, GroovyParser parser) {
    if (!ParserUtils.getToken(builder, kCLASS)) {
      return false;
    }

    String name;
    if (!mIDENT.equals(builder.getTokenType())) {
      builder.error(GroovyBundle.message("identifier.expected"));
      return false;
    } else {
      name = builder.getTokenText();
      builder.advanceLexer();
    }

    ParserUtils.getToken(builder, mNLS);

    TypeParameters.parse(builder);

    if (kEXTENDS.equals(builder.getTokenType())) {
      ExtendsClause.parse(builder);
      ParserUtils.getToken(builder, mNLS);
    }

    if (kIMPLEMENTS.equals(builder.getTokenType())) {
      ImplementsClause.parse(builder);
    }

    ParserUtils.getToken(builder, mNLS);

    if (mLCURLY.equals(builder.getTokenType())) {
      parseClassBody(builder, name, parser);
    } else {
      builder.error(GroovyBundle.message("lcurly.expected"));
      return true;
    }

    return true;
  }

  private static boolean parseEnum(PsiBuilder builder, GroovyParser parser) {
    if (!ParserUtils.getToken(builder, kENUM)) {
      return false;
    }

    String name;
    if (!mIDENT.equals(builder.getTokenType())) {
      builder.error(GroovyBundle.message("identifier.expected"));
      return false;
    } else {
      name = builder.getTokenText();
      builder.advanceLexer();
    }

    if (WRONGWAY.equals(ImplementsClause.parse(builder))) {
      return true;
    }

    Separators.parse(builder);

    EnumBlock.parse(builder, name, parser);

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
      Separators.parse(builder);
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

  public static boolean parseClassBody(PsiBuilder builder, String className, GroovyParser parser) {
    //see also InterfaceBlock, EnumBlock, AnnotationBlock
    //allow errors
    PsiBuilder.Marker cbMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mLCURLY)) {
      builder.error(GroovyBundle.message("lcurly.expected"));
      cbMarker.rollbackTo();
      return false;
    }

    Separators.parse(builder);

    while (!builder.eof() && builder.getTokenType() != mRCURLY) {
      if (!ClassMember.parse(builder, className, parser)) {
        builder.getTokenType();
        builder.advanceLexer();
      }
      if (!(Separators.parse(builder) || ParserUtils.lookAhead(builder, mRCURLY))) {
        builder.error(GroovyBundle.message("separator.or.rcurly.expected"));
      }
    }

    ParserUtils.getToken(builder, mRCURLY, GroovyBundle.message("rcurly.expected"));

    cbMarker.done(CLASS_BODY);
    return true;
  }
}
