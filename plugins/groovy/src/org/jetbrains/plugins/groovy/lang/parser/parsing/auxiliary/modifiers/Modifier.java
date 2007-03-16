package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.plugins.groovy.lang.parser.parsing.Construction;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 14.03.2007
 */

/*
 * Modifier ::= private
 *            | public
 *            | protected
 *            | static
 *            | transient
 *            | final
 *            | abstract
 *            | native
 *            | threadsafe
 *            | synchronized
 *            | volatile
 *            | srtictfp
 */

public class Modifier implements Construction {
  public static TokenSet modifiers = TokenSet.create(
      kPRIVATE,
      kPUBLIC,
      kPROTECTED,
      kSTATIC,
      kTRANSIENT,
      kFINAL,
      kABSTRACT,
      kNATIVE,
      kTHREADSAFE,
      kSYNCHRONIZED,
      kVOLATILE,
      kSTRICTFP
  );

  public static IElementType parse(PsiBuilder builder) {
    if (ParserUtils.validateToken(builder, modifiers)) {
      ParserUtils.eatElement(builder, builder.getTokenType());
      return MODIFIER;
    } else {
//      builder.error(GroovyBundle.message("modifier.expected"));
      return WRONGWAY;
    }
  }
}
