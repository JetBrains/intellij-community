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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.blocks;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members.AnnotationMember;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 18.03.2007
 */
public class AnnotationBlock implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {
    //see also InterfaceBlock, EnumBlock, AnnotationBlock
    PsiBuilder.Marker abMarker = builder.mark();

    if (!ParserUtils.getToken(builder, mLCURLY)) {
      builder.error(GroovyBundle.message("lcurly.expected"));
      abMarker.rollbackTo();
      return WRONGWAY;
    }

    AnnotationMember.parse(builder);

    IElementType sep = Separators.parse(builder);

    while (!WRONGWAY.equals(sep)) {
      AnnotationMember.parse(builder);

      sep = Separators.parse(builder);
    }

    if (builder.getTokenType() != mRCURLY) {
      builder.error(GroovyBundle.message("rcurly.expected"));
    }

    while (!builder.eof() && !ParserUtils.getToken(builder, mRCURLY)) {
      AnnotationMember.parse(builder/*, className*/);

      builder.advanceLexer();
    }

    abMarker.done(ANNOTATION_BLOCK);
    return ANNOTATION_BLOCK;
  }
}
