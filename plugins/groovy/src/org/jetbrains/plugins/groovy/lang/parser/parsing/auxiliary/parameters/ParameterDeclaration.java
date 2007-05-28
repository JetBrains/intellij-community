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

package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.VariableInitializer;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations.Annotation;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeSpec;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * @author: Dmitry.Krasilschikov, ilyas
 */
public class ParameterDeclaration implements GroovyElementTypes {

  /**
   * @param builder
   * @param ending  Given ending: -> or )
   * @return
   */
  public static GroovyElementType parse(PsiBuilder builder, IElementType ending) {
    PsiBuilder.Marker pdMarker = builder.mark();

    // Parse optional modifier(s)
    parseOptionalModifier(builder);

    PsiBuilder.Marker rb = builder.mark();
    TypeSpec.parseStrict(builder);
    if (!mIDENT.equals(builder.getTokenType())) {
      rb.rollbackTo();
    } else {
      rb.drop();
    }

    // Possible it is a parameter, not statement
    boolean hasDots = ParserUtils.getToken(builder, mTRIPLE_DOT);

    if (ParserUtils.getToken(builder, mIDENT)) {
      if (mASSIGN.equals(builder.getTokenType())) {
        VariableInitializer.parse(builder);
      }
      if (builder.getTokenType() == mCOMMA ||
          builder.getTokenType() == ending ||
              ParserUtils.lookAhead(builder, mNLS, ending)) {
        pdMarker.done(PARAMETER);
        return PARAMETER;
      } else {
        pdMarker.rollbackTo();
        return WRONGWAY;
      }
    } else {
      // If has triple dots
      if (hasDots) {
        builder.error(GroovyBundle.message("identifier.expected"));
        pdMarker.done(PARAMETER);
        return PARAMETER;
      } else {
        pdMarker.rollbackTo();
        return WRONGWAY;
      }
    }
  }

  /**
   * Parses optional modifiers
   *
   * @param builder Given builder
   */
  private static void parseOptionalModifier(PsiBuilder builder) {

    Set<IElementType> modSet = new HashSet<IElementType>();

    PsiBuilder.Marker marker = builder.mark();

    while (builder.getTokenType() == kFINAL ||
            builder.getTokenType() == kDEF ||
            builder.getTokenType() == mAT) {

      if (kFINAL.equals(builder.getTokenType())) {
        if (modSet.contains(kFINAL)) {
          ParserUtils.wrapError(builder, GroovyBundle.message("duplicate.modifier"));
        } else {
          builder.advanceLexer();
          modSet.add(kFINAL);
        }
        ParserUtils.getToken(builder, mNLS);
      } else if (kDEF.equals(builder.getTokenType())) {
        if (modSet.contains(kDEF)) {
          ParserUtils.wrapError(builder, GroovyBundle.message("duplicate.modifier"));
        } else {
          builder.advanceLexer();
          modSet.add(kDEF);
        }
        ParserUtils.getToken(builder, mNLS);
      } else if (!WRONGWAY.equals(Annotation.parse(builder))) {
        ParserUtils.getToken(builder, mNLS);
      }
    }
    marker.done(PARAMETER_MODIFIERS);
  }

}
