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
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks.OpenOrClosableBlock;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration.Declaration;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.TypeDefinition;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.TypeDeclarationStart;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 20.03.2007
 */
public class ClassMember implements GroovyElementTypes {
  public static boolean parse(PsiBuilder builder, String className) {

    if (ConstructorDefinition.parse(builder, className)) return true;

    //declaration
    PsiBuilder.Marker declMarker = builder.mark();
    if (Declaration.parse(builder, true)) {
      declMarker.drop();
      return true;
    } else {
      declMarker.rollbackTo();
    }

    //type definition
    PsiBuilder.Marker typeDeclStartMarker = builder.mark();
    if (TypeDeclarationStart.parse(builder)) {
      typeDeclStartMarker.rollbackTo();

      if (!TypeDefinition.parse(builder)) {
        builder.error(GroovyBundle.message("type.definition.expected"));
        return false;
      }
      return true;
    }
    typeDeclStartMarker.rollbackTo();

    //static compound statement
    PsiBuilder.Marker initMarker = builder.mark();
    if (kSTATIC == builder.getTokenType()) {
      PsiBuilder.Marker modMarker = builder.mark();
      ParserUtils.getToken(builder, kSTATIC);
      modMarker.done(MODIFIERS);
      if (OpenOrClosableBlock.parseOpenBlock(builder)) {
        initMarker.done(CLASS_INITIALIZER);
        return true;
      } else {
        initMarker.rollbackTo();
        ParserUtils.getToken(builder, kSTATIC);
        builder.error(GroovyBundle.message("compound.statemenet.expected"));
        return false;
      }
    }

    builder.mark().done(MODIFIERS);
    if (OpenOrClosableBlock.parseOpenBlock(builder)) {
      initMarker.done(CLASS_INITIALIZER);
      return true;
    }

    initMarker.rollbackTo();

    return false;
  }
}
