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

package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.parameters;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.VariableInitializer;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations.Annotation;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeSpec;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;

import java.util.HashSet;
import java.util.Set;

/**
 * @author: Dmitry.Krasilschikov, ilyas
 */
public class ParameterDeclaration implements GroovyElementTypes {

  public static boolean parseTraditionalForParameter(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker pdMarker = builder.mark();

    // Parse optional modifier(s)
    final boolean hasModifiers = parseOptionalModifier(builder, parser);

    PsiBuilder.Marker rb = builder.mark();

    final ReferenceElement.ReferenceElementResult result = TypeSpec.parseStrict(builder, true);

    if (result == ReferenceElement.ReferenceElementResult.FAIL && !hasModifiers) {
      rb.drop();
      pdMarker.rollbackTo();
      return false;
    }

    if (mIDENT.equals(builder.getTokenType())) {
      rb.drop();
    }
    else {
      rb.rollbackTo();
      if (!hasModifiers) {
        pdMarker.rollbackTo();
        return false;
      }
    }

    if (ParserUtils.getToken(builder, mIDENT)) {
      if (mASSIGN.equals(builder.getTokenType())) {
        VariableInitializer.parse(builder, parser);
      }
      pdMarker.done(PARAMETER);
      return true;
    }
    else {
      pdMarker.rollbackTo();
      return false;
    }
  }

  public static boolean parseSimpleParameter(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker pdMarker = builder.mark();

    // Parse optional modifier(s)
    parseOptionalModifier(builder, parser);

    PsiBuilder.Marker rb = builder.mark();

    final ReferenceElement.ReferenceElementResult result = TypeSpec.parseStrict(builder, false);

    if (mIDENT.equals(builder.getTokenType()) || (mTRIPLE_DOT.equals(builder.getTokenType()))) {
      rb.drop();
    }
    else if (result == ReferenceElement.ReferenceElementResult.REF_WITH_TYPE_PARAMS) {
      rb.drop();
      pdMarker.error(GroovyBundle.message("identifier.expected"));
      return true;
    }
    else  {
      rb.rollbackTo();
    }

    // Possible it is a parameter, not statement
    boolean hasDots = ParserUtils.getToken(builder, mTRIPLE_DOT);

    if (ParserUtils.getToken(builder, mIDENT)) {
      if (mASSIGN.equals(builder.getTokenType())) {
        VariableInitializer.parse(builder, parser);
      }
      pdMarker.done(PARAMETER);
      return true;
    }
    else if (hasDots) {
      pdMarker.error(GroovyBundle.message("identifier.expected"));
      return true;
    }
    else {
      pdMarker.rollbackTo();
      return false;
    }
  }

  public static boolean parseCatchParameter(PsiBuilder builder, GroovyParser parser) {
    PsiBuilder.Marker pdMarker = builder.mark();

    // Parse optional modifier(s)
    parseOptionalModifier(builder, parser);

    final PsiBuilder.Marker disjunctionMarker = builder.mark();

    PsiBuilder.Marker rb = builder.mark();

    int typeCount = 0;
    do {
      typeCount++;
      rb.drop();
      rb = builder.mark();
      final ReferenceElement.ReferenceElementResult result = TypeSpec.parseStrict(builder, false);
      if (result == ReferenceElement.ReferenceElementResult.FAIL && ParserUtils.lookAhead(builder, mBOR)) {
        builder.error(GroovyBundle.message("type.expected"));
      }
      else {
        if (builder.getTokenType() == mTRIPLE_DOT) {
          builder.error(GroovyBundle.message("triple.is.not.expected.here"));
          builder.advanceLexer();
        }
      }
    }
    while (ParserUtils.getToken(builder, mBOR));



    if (mIDENT == builder.getTokenType()) {
      rb.drop();
    }
    else if (typeCount == 1) {
      rb.rollbackTo();
      typeCount--;
    }
    else {
      builder.error(GroovyBundle.message("identifier.expected"));
      rb.drop();
    }

    if (typeCount > 1) {
      disjunctionMarker.done(DISJUNCTION_TYPE_ELEMENT);
    }
    else {
      disjunctionMarker.drop();
    }

    if (ParserUtils.getToken(builder, mIDENT)) {
      pdMarker.done(PARAMETER);
      return true;
    }
    else {
      pdMarker.drop();
      return false;
    }
  }

  /**
   * Parses optional modifiers
   *
   * @param builder Given builder
   */
  private static boolean parseOptionalModifier(PsiBuilder builder, GroovyParser parser) {

    Set<IElementType> modSet = new HashSet<IElementType>();

    PsiBuilder.Marker marker = builder.mark();

    boolean hasModifiers = false;
    while (builder.getTokenType() == kFINAL ||
           builder.getTokenType() == kDEF ||
           builder.getTokenType() == mAT) {
      hasModifiers = true;
      if (kFINAL.equals(builder.getTokenType())) {
        if (modSet.contains(kFINAL)) {
          ParserUtils.wrapError(builder, GroovyBundle.message("duplicate.modifier", PsiModifier.FINAL));
        }
        else {
          builder.advanceLexer();
          modSet.add(kFINAL);
        }
        ParserUtils.getToken(builder, mNLS);
      }
      else if (kDEF.equals(builder.getTokenType())) {
        if (modSet.contains(kDEF)) {
          ParserUtils.wrapError(builder, GroovyBundle.message("duplicate.modifier", GrModifier.DEF));
        }
        else {
          builder.advanceLexer();
          modSet.add(kDEF);
        }
        ParserUtils.getToken(builder, mNLS);
      }
      else { // @
        if (!Annotation.parse(builder, parser)) {
          ParserUtils.wrapError(builder, GroovyBundle.message("annotation.expected"));
        }
        ParserUtils.getToken(builder, mNLS);
      }
    }
    marker.done(MODIFIERS);
    return hasModifiers;
  }
}
