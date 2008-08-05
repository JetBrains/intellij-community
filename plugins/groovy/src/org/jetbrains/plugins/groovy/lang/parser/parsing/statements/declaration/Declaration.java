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
  public static boolean parse(PsiBuilder builder, boolean isInClass) {
    return parse(builder, isInClass, false);
  }

  public static boolean parse(PsiBuilder builder, boolean isInClass, boolean isInAnnotation) {
    PsiBuilder.Marker declMarker = builder.mark();
    //allows error messages
    boolean modifiersParsed = Modifiers.parse(builder);

    if (modifiersParsed && mLT == builder.getTokenType()) {
      TypeParameters.parse(builder);
      PsiBuilder.Marker checkMarker = builder.mark(); //point to begin of type or variable

      if (!TypeSpec.parse(builder, true)) { //if type wasn't recognized trying parse VaribleDeclaration
        checkMarker.rollbackTo();
      } else {
        checkMarker.drop();
      }
      IElementType decl = VariableDefinitions.parseDefinitions(builder, isInClass, false, false, true, modifiersParsed);

      if (WRONGWAY.equals(decl)) {
        declMarker.error(GroovyBundle.message("method.definitions.expected"));
      } else {
        declMarker.done(METHOD_DEFINITION);
      }
      return true;

    } else if (modifiersParsed) {

      PsiBuilder.Marker checkMarker = builder.mark(); //point to begin of type or variable

      if (!TypeSpec.parse(builder, false)) { //if type wasn't recognized trying parse VaribleDeclaration
        checkMarker.rollbackTo();

        if (isInAnnotation) {
          builder.error(GroovyBundle.message("type.expected"));
        }

        IElementType varDecl = VariableDefinitions.parse(builder, isInClass, modifiersParsed);

        if (WRONGWAY.equals(varDecl)) {
          builder.error(GroovyBundle.message("variable.definitions.expected"));
          declMarker.rollbackTo();
          return false;
        } else {
          declMarker.done(varDecl);
          return true;
        }

      } else {  //type was recognized
        IElementType varDeclarationTop = VariableDefinitions.parse(builder, isInClass, modifiersParsed);

        if (WRONGWAY.equals(varDeclarationTop)) {
          checkMarker.rollbackTo();

          if (isInAnnotation) {
            builder.error(GroovyBundle.message("type.expected"));
          }

          IElementType varDecl = VariableDefinitions.parse(builder, isInClass, modifiersParsed);

          if (WRONGWAY.equals(varDecl)) {
            builder.error(GroovyBundle.message("variable.definitions.expected"));
            declMarker.rollbackTo();
            return false;
          } else {
            declMarker.done(varDecl);
            return true;
          }
        } else {
          checkMarker.drop();
          declMarker.done(varDeclarationTop);
          return true;
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
        declMarker.rollbackTo();
        return false;
      }

      boolean typeParsed = false;
      if (!ParserUtils.lookAhead(builder, mIDENT, mLPAREN)) {
        typeParsed = TypeSpec.parse(builder, true);
        //type specification starts with upper case letter
        if (!typeParsed) {
          builder.error(GroovyBundle.message("type.specification.expected"));
          declMarker.rollbackTo();
          return false;
        }
      }

      IElementType varDef = VariableDefinitions.parse(builder, isInClass, modifiersParsed || typeParsed);
      if (varDef != WRONGWAY) {
        declMarker.done(varDef);
        return true;
      } else if (isInClass && typeParsed) {
        declMarker.drop();
        return typeParsed;
      }

      declMarker.rollbackTo();
      return false;
    }
  }
}

