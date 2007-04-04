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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.expressions;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.BranchStatement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration.DeclarationStart;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.annotations.Annotation;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.Modifiers;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeSpec;

/**
 * @autor: Dmitry.Krasilschikov, Ilya Sergey
 *
 */
public class StrictContextExpression implements GroovyElementTypes
{
  public static GroovyElementType parse(PsiBuilder builder)
  {

    if (BranchStatement.BRANCH_KEYWORDS.contains(builder.getTokenType())){
      return BranchStatement.parse(builder);
    }
    if (mAT.equals(builder.getTokenType())){
      return Annotation.parse(builder);
    }


    

/*
    if (DeclarationStart.parse(builder)) {
      return SingleDeclara
    }
*/
    // TODO implement two other cases

    return ExpressionStatement.argParse(builder);
  }

  public static GroovyElementType singleDeclarationParse(PsiBuilder builder){
/*
    if (!WRONGWAY.equals(Modifiers.parse(builder))) {
      
    } else {
      if (!WRONGWAY.equals(TypeSpec.parse(builder))) {

      } else {
        builder.error();
      }
    }
*/

    return  DECLARATION;
  }
}
