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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.Modifiers;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeParameters;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeSpec;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

import static org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement.ReferenceElementResult.fail;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 14.03.2007
 */

/*
 * Declaration ::= modifiers [TypeSpec] VariableDefinitions
 *                  | TypeSpec VariableDefinitions
 */

public class Declaration implements GroovyElementTypes {
  public static boolean parse(PsiBuilder builder, boolean isInClass, GroovyParser parser) {
    return parse(builder, isInClass, false, parser);
  }

  public static boolean parse(PsiBuilder builder, boolean isInClass, boolean isInAnnotation, GroovyParser parser) {
    PsiBuilder.Marker declMarker = builder.mark();
    //allows error messages
    boolean modifiersParsed = Modifiers.parse(builder, parser);

    final boolean methodStart = mLT == builder.getTokenType();
    final IElementType type = parseAfterModifiers(builder, isInClass, isInAnnotation, parser, declMarker, modifiersParsed);
    if (type == WRONGWAY) {
      if (modifiersParsed && methodStart) {
        declMarker.error(GroovyBundle.message("method.definitions.expected"));
        return false;
      }

      declMarker.rollbackTo();
      if (modifiersParsed) {
        builder.error(GroovyBundle.message("variable.definitions.expected"));
      }

      return false;
    }

    if (type != null) {
      declMarker.done(type);
    } else {
      declMarker.drop();
    }
    return true;
  }

  @Nullable
  public static IElementType parseAfterModifiers(PsiBuilder builder,
                                            boolean isInClass,
                                            boolean isInAnnotation,
                                            GroovyParser parser,
                                            PsiBuilder.Marker declMarker, boolean modifiersParsed) {
    if (modifiersParsed && mLT == builder.getTokenType()) {
      TypeParameters.parse(builder);
      PsiBuilder.Marker checkMarker = builder.mark(); //point to begin of type or variable

      if (TypeSpec.parse(builder, true) == fail) { //if type wasn't recognized trying parse VariableDeclaration
        checkMarker.rollbackTo();
      } else {
        checkMarker.drop();
      }
      IElementType decl = VariableDefinitions.parseDefinitions(builder, isInClass, false, false, true, modifiersParsed, false, parser);

      if (WRONGWAY.equals(decl)) {
        return WRONGWAY;
      }

      return METHOD_DEFINITION;
    }

    if (modifiersParsed) {

      PsiBuilder.Marker checkMarker = builder.mark(); //point to begin of type or variable

      if (TypeSpec.parse(builder, false) == fail) { //if type wasn't recognized trying parse VariableDeclaration
        checkMarker.rollbackTo();

        if (isInAnnotation) {
          builder.error(GroovyBundle.message("type.expected"));
        }

        //current token isn't identifier
        IElementType varDecl = VariableDefinitions.parse(builder, isInClass, modifiersParsed, parser);

        if (WRONGWAY.equals(varDecl)) {
          return WRONGWAY;
        }
        return varDecl;
      } else {  //type was recognized, identifier here
        //starts after type
        IElementType varDeclarationTop = VariableDefinitions.parse(builder, isInClass, modifiersParsed, false, parser);

        if (WRONGWAY.equals(varDeclarationTop)) {
          checkMarker.rollbackTo();

          if (isInAnnotation) {
            builder.error(GroovyBundle.message("type.expected"));
          }

          //starts before "type" identifier, here can't be tuple, because next token is identifier (we are in "type recognized" branch)
          IElementType varDecl = VariableDefinitions.parse(builder, isInClass, modifiersParsed, false, parser);

          if (WRONGWAY.equals(varDecl)) {
            return WRONGWAY;
          } else {
            return varDecl;
          }
        } else {
          checkMarker.drop();
          return varDeclarationTop;
        }
      }
    } else {

      //if definition starts with lower case letter than it can be just call expression

      String text = builder.getTokenText();
      if (!builder.eof()
          && !TokenSets.BUILT_IN_TYPE.contains(builder.getTokenType())
          && text != null
          && (Character.isLowerCase((text.charAt(0))) || !Character.isLetter(text.charAt(0))) &&
          (ParserUtils.lookAhead(builder, mIDENT, mIDENT) || ParserUtils.lookAhead(builder, mIDENT, mLPAREN))) {
        //call expression
        return WRONGWAY;
      }

      boolean typeParsed = false;
      if (!ParserUtils.lookAhead(builder, mIDENT, mLPAREN)) {
        typeParsed = TypeSpec.parse(builder, true) != fail;
        //type specification starts with upper case letter
        if (!typeParsed) {
          builder.error(GroovyBundle.message("type.specification.expected"));
          return WRONGWAY;
        }
      }

      IElementType varDef = VariableDefinitions.parseDefinitions(builder, isInClass, false, false, false, typeParsed, false, parser);
      if (varDef != WRONGWAY) {
        return varDef;
      } else if (isInClass && typeParsed) {
        return typeParsed ? null : WRONGWAY;
      }

      return WRONGWAY;
    }
  }
}

