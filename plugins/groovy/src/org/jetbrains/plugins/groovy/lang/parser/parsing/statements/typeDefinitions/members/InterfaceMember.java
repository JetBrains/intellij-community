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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.ModifiersOptional;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration.Declaration;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.TypeDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeDeclarationStart;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 20.03.2007
 */
public class InterfaceMember implements GroovyElementTypes {
  public static IElementType parse(PsiBuilder builder) {

    //constructor
    PsiBuilder.Marker constructorMarker = builder.mark();
    ModifiersOptional.parse(builder);

    GroovyElementType constructorDef = ConstructorDefinition.parse(builder);

    if (WRONGWAY.equals(constructorDef)) {
      constructorMarker.rollbackTo();
    } else {
      constructorMarker.done(constructorDef);
      builder.error(GroovyBundle.message("interface.must.has.no.constructor"));
      return constructorDef;
    }

    //declaration
    PsiBuilder.Marker declMarker = builder.mark();
    GroovyElementType declType = Declaration.parse(builder, true, false);
    if (WRONGWAY.equals(declType)) {
      declMarker.rollbackTo();
    } else {
      declMarker.drop();
      return declType;
    }

    //declaration
//    PsiBuilder.Marker declMarker = builder.mark();
//    if (DeclarationStart.parse(builder)) {
//      declMarker.rollbackTo();
//      return Declaration.parse(builder);
//    }
//    declMarker.rollbackTo();

    //type definition
    PsiBuilder.Marker typeDeclStartMarker = builder.mark();
    if (TypeDeclarationStart.parse(builder)) {
      typeDeclStartMarker.rollbackTo();

      IElementType typeDef = TypeDefinition.parse(builder);
      if (WRONGWAY.equals(typeDef)) {
        return WRONGWAY;
      }
      return typeDef;
    }
    typeDeclStartMarker.rollbackTo();

    //static compound statement
    if (ParserUtils.getToken(builder, kSTATIC)) {
      if (!WRONGWAY.equals(OpenOrClosableBlock.parseOpenBlock(builder))) {
        builder.error(GroovyBundle.message("interface.must.has.no.static.compound.statemenet"));
        return STATIC_COMPOUND_STATEMENT;
      } else {
        builder.error(GroovyBundle.message("compound.statemenet.expected"));
        return WRONGWAY;
      }
    }

    if (!WRONGWAY.equals(OpenOrClosableBlock.parseOpenBlock(builder))) {
      builder.error(GroovyBundle.message("interface.must.has.no.compound.statemenet"));
      return COMPOUND_STATEMENT;
    }

    return WRONGWAY;
  }
}