package org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary;

import org.jetbrains.plugins.groovy.lang.parser.parsing.Construction;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.lang.PsiBuilder;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 14.03.2007
 */
public class Modifier implements Construction {
  public static TokenSet first = TokenSet.create(
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
    return WRONGWAY;
  }

}
