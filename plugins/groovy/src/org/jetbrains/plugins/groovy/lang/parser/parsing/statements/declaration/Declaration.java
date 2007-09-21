/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.Modifiers;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeParameters;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeSpec;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 14.03.2007
 */

/*
 * Declaration ::= modifiers [TypeSpec] VariableDefinitions
 *                  | TypeSpec VariableDefinitions
 */

public class Declaration implements GroovyElementTypes {
  public static GroovyElementType parse(PsiBuilder builder, boolean isInClass) {
    return parse(builder, isInClass, false);
  }

  public static GroovyElementType parse(PsiBuilder builder, boolean isInClass, boolean isInAnnotation) {
    PsiBuilder.Marker declMarker = builder.mark();
    //allows error messages
    IElementType modifiers = Modifiers.parse(builder);

    if (WRONGWAY != modifiers && mLT == builder.getTokenType()) {
      TypeParameters.parse(builder);
      PsiBuilder.Marker checkMarker = builder.mark(); //point to begin of type or variable

      if (WRONGWAY.equals(TypeSpec.parse(builder, true))) { //if type wasn't recognized trying parse VaribleDeclaration
        checkMarker.rollbackTo();
      } else {
        checkMarker.drop();
      }
      GroovyElementType decl = VariableDefinitions.parseDefinitions(builder, isInClass, false, false, true);

      if (WRONGWAY.equals(decl)) {
        declMarker.error(GroovyBundle.message("method.definitions.expected"));
      } else {
        declMarker.done(METHOD_DEFINITION);
      }
      return METHOD_DEFINITION;

    } else if (!WRONGWAY.equals(modifiers)) {

      PsiBuilder.Marker checkMarker = builder.mark(); //point to begin of type or variable

      if (WRONGWAY.equals(TypeSpec.parse(builder, false))) { //if type wasn't recognized trying parse VaribleDeclaration
        checkMarker.rollbackTo();

        if (isInAnnotation) {
          builder.error(GroovyBundle.message("type.expected"));
        }

        GroovyElementType varDecl = VariableDefinitions.parse(builder, isInClass);

        if (WRONGWAY.equals(varDecl)) {
          builder.error(GroovyBundle.message("variable.definitions.expected"));
          declMarker.rollbackTo();
          return WRONGWAY;
        } else {
          declMarker.done(varDecl);
          return varDecl;
        }

      } else {  //type was recognized
        GroovyElementType varDeclarationTop = VariableDefinitions.parse(builder, isInClass);

        if (WRONGWAY.equals(varDeclarationTop)) {
          checkMarker.rollbackTo();

          if (isInAnnotation) {
            builder.error(GroovyBundle.message("type.expected"));
          }

          GroovyElementType varDecl = VariableDefinitions.parse(builder, isInClass);

          if (WRONGWAY.equals(varDecl)) {
            builder.error(GroovyBundle.message("variable.definitions.expected"));
            declMarker.rollbackTo();
            return WRONGWAY;
          } else {
            declMarker.done(varDecl);
            return varDecl;
          }
        } else {
          checkMarker.drop();
          declMarker.done(varDeclarationTop);
          return varDeclarationTop;
        }
      }
    } else {

      //if definition starts with lower case letter than it can be just call expression

      String text = builder.getTokenText();
      if (!builder.eof()
          && !TokenSets.BUILT_IN_TYPE.contains(builder.getTokenType())
          && text != null
          && Character.isLowerCase(text.charAt(0)) &&
          (ParserUtils.lookAhead(builder, mIDENT, mIDENT) || ParserUtils.lookAhead(builder, mIDENT, mLPAREN))) {
        //call expression
        declMarker.rollbackTo();
        return WRONGWAY;
      }

      GroovyElementType typeParseResult = null;
      if (!ParserUtils.lookAhead(builder, mIDENT, mLPAREN)) {
        //type specification starts with upper case letter
        typeParseResult = TypeSpec.parse(builder, true);
        if (WRONGWAY.equals(typeParseResult)) {
          builder.error(GroovyBundle.message("type.specification.expected"));
          declMarker.rollbackTo();
          return WRONGWAY;
        }
      }

      GroovyElementType varDef = VariableDefinitions.parse(builder, isInClass);
      if (varDef != WRONGWAY) {
        declMarker.done(varDef);
        return varDef;
      } else if (isInClass && typeParseResult != null) {
        declMarker.drop();
        return typeParseResult;
      }

      declMarker.rollbackTo();
      return WRONGWAY;
    }
  }
}

