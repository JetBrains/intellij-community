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

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.members;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParser;
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
  public static boolean parse(PsiBuilder builder, String interfaceName, GroovyParser parser) {
    //constructor
    if (ConstructorDefinition.parse(builder, interfaceName, parser)) return true;

    //declaration
    PsiBuilder.Marker declMarker = builder.mark();
    if (Declaration.parse(builder, true, parser)) {
      declMarker.drop();
      return true;
    } else {
      declMarker.rollbackTo();
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
    if (TypeDeclarationStart.parse(builder, parser)) {
      typeDeclStartMarker.rollbackTo();

      return TypeDefinition.parseTypeDefinition(builder, parser);
    }
    typeDeclStartMarker.rollbackTo();

    // static class initializer
    if (ParserUtils.getToken(builder, kSTATIC)) {
      if (OpenOrClosableBlock.parseOpenBlock(builder, parser)) {
        builder.error(GroovyBundle.message("interface.must.has.no.static.compound.statemenet"));
        return true;
      } else {
        builder.error(GroovyBundle.message("compound.statemenet.expected"));
        return false;
      }
    }

    // class initializer
    if (OpenOrClosableBlock.parseOpenBlock(builder, parser)) {
      builder.error(GroovyBundle.message("interface.must.has.no.compound.statemenet"));
      return true;
    }

    return false;
  }
}