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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.VariableInitializer;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations.Annotation;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.Modifiers;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.BranchStatement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration.DeclarationStart;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeSpec;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

import static org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement.ReferenceElementResult.fail;

/**
 * @autor: Dmitry.Krasilschikov, ilyas
 */
public class StrictContextExpression implements GroovyElementTypes {
  public static boolean parse(PsiBuilder builder, GroovyParser parser) {

    if (BranchStatement.BRANCH_KEYWORDS.contains(builder.getTokenType())) {
      return BranchStatement.parse(builder, parser);
    }
    if (mAT.equals(builder.getTokenType())) {
      return Annotation.parse(builder, parser);
    }
    if (DeclarationStart.parse(builder, parser)) {
      singleDeclarationParse(builder, parser);
      return true;
    }
    return ExpressionStatement.argParse(builder, parser);
  }

  public static void singleDeclarationParse(PsiBuilder builder, GroovyParser parser) {

    PsiBuilder.Marker marker = builder.mark();
    if (Modifiers.parse(builder, parser)) {
      PsiBuilder.Marker rb = builder.mark();
      TypeSpec.parse(builder);
      if (!mIDENT.equals(builder.getTokenType())) {
        rb.rollbackTo();
      } else {
        rb.drop();
      }
      ParserUtils.getToken(builder, mIDENT, GroovyBundle.message("identifier.expected"));
      if (mASSIGN.equals(builder.getTokenType())) {
        VariableInitializer.parse(builder, parser);
      }
      marker.done(VARIABLE_DEFINITION);
    } else {
      if (TypeSpec.parse(builder) != fail) {
        ParserUtils.getToken(builder, mIDENT, GroovyBundle.message("identifier.expected"));
        if (mASSIGN.equals(builder.getTokenType())) {
          VariableInitializer.parse(builder, parser);
        }
        marker.done(VARIABLE_DEFINITION);
      } else {
        builder.error(GroovyBundle.message("type.specification.expected"));
      }
    }
  }
}
