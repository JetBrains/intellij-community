package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.identifier.UpperCaseIdent;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.Modifiers;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.auxilary.BalancedTokens;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.BuiltInType;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.QualifiedTypeName;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 14.03.2007
 */

/**
 * DeclarationStart ::= "def"
 * | modifier
 * | @IDENT
 * | (upperCaseIdent | builtInType | QulifiedTypeName)  {LBRACK balacedTokens RBRACK} IDENT
 */

public class DeclarationStart implements GroovyElementTypes {
  public static boolean parse(PsiBuilder builder) {
    IElementType elementType;

    //def
    if (ParserUtils.getToken(builder, kDEF)) return parseNextTokenInDeclaration(builder);

    //Modifiers
    elementType = Modifiers.parse(builder);
    if (!WRONGWAY.equals(elementType)) {
      return parseNextTokenInDeclaration(builder);
    }

    //@IDENT
    if (ParserUtils.getToken(builder, mAT)) {
      return ParserUtils.getToken(builder, mIDENT);
    }

    // (upperCaseIdent | builtInType | QulifiedTypeName)  {LBRACK balacedTokens RBRACK} IDENT
    if (!WRONGWAY.equals(UpperCaseIdent.parse(builder)) || !WRONGWAY.equals(BuiltInType.parse(builder)) || !WRONGWAY.equals(QualifiedTypeName.parse(builder))) {

      IElementType balancedTokens;

      do {
        balancedTokens = parseBalancedTokensInBrackets(builder);
        if (!BALANCED_TOKENS.equals(balancedTokens)) {
          return false;
        }
      } while (!WRONGWAY.equals(balancedTokens));

      //IDENT
      return ParserUtils.getToken(builder, mIDENT);

    } else {
//      builder.error(GroovyBundle.message("upper.case.ident.or.builtIn.type.or.qualified.type.name.expected"));
      return false;
    }
  }
  private static boolean parseNextTokenInDeclaration(PsiBuilder builder) {
    return ParserUtils.lookAhead(builder, mIDENT) ||
        TokenSets.BUILT_IN_TYPE.contains(builder.getTokenType()) ||
        TokenSets.MODIFIERS.contains(builder.getTokenType()) ||
          ParserUtils.lookAhead(builder, kDEF) ||
          ParserUtils.lookAhead(builder, mAT) ||
          ParserUtils.lookAhead(builder, mSTRING_LITERAL);
  }

  private static IElementType parseBalancedTokensInBrackets(PsiBuilder builder) {
    PsiBuilder.Marker btm = builder.mark();

    if (!ParserUtils.getToken(builder, mLBRACK, GroovyBundle.message("lbrack.expected"))) {
      btm.rollbackTo();
      return WRONGWAY;
    }

    if (WRONGWAY.equals(BalancedTokens.parse(builder))) {
      btm.rollbackTo();
      return WRONGWAY;
    }

    if (!ParserUtils.getToken(builder, mRBRACK, GroovyBundle.message("rbrack.expected"))) {
      btm.rollbackTo();
      return WRONGWAY;
    }

    btm.done(BALANCED_TOKENS);

    return BALANCED_TOKENS;
  }
}