// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.imports;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.typeDefinitions.ReferenceElement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * Import identifier
 *
 * @author ilyas
 */
public class ImportReference {

  public static boolean parse(PsiBuilder builder) {

    if (!TokenSets.CODE_REFERENCE_ELEMENT_NAME_TOKENS.contains(builder.getTokenType())) {
      return false;
    }

    if (ReferenceElement.parseForImport(builder) == ReferenceElement.ReferenceElementResult.FAIL) {
      return false;
    }

    if (ParserUtils.getToken(builder, GroovyTokenTypes.mDOT)) {
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      if (!ParserUtils.getToken(builder, GroovyTokenTypes.mSTAR)) {
        builder.error(GroovyBundle.message("identifier.expected"));
      }
    }

    final PsiBuilder.Marker aliasMarker = builder.mark();
    if (ParserUtils.getToken(builder, GroovyTokenTypes.kAS)) {
      ParserUtils.getToken(builder, GroovyTokenTypes.mNLS);
      if (!ParserUtils.getToken(builder, GroovyTokenTypes.mIDENT)) {
        builder.error(GroovyBundle.message("identifier.expected"));
      }
      aliasMarker.done(GroovyElementTypes.IMPORT_ALIAS);
    }
    else {
      aliasMarker.drop();
    }

    return true;
  }
}
