package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.identifier.UpperCaseIdent;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.modifiers.Modifier;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.auxilary.BalancedTokens;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.BuiltInType;
import org.jetbrains.plugins.groovy.lang.parser.parsing.types.QualifiedTypeName;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;

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

    //Modifier
    elementType = Modifier.parse(builder);
    if (!tWRONG_SET.contains(elementType)) {
      return parseNextTokenInDeclaration(builder);
    }

    //@IDENT
    if (ParserUtils.getToken(builder, mAT)) {
      return ParserUtils.getToken(builder, mIDENT);
    }

    // (upperCaseIdent | builtInType | QulifiedTypeName)  {LBRACK balacedTokens RBRACK} IDENT
    if (!tWRONG_SET.contains(UpperCaseIdent.parse(builder)) || !tWRONG_SET.contains(BuiltInType.parse(builder)) || !tWRONG_SET.contains(QualifiedTypeName.parse(builder))) {

      IElementType balancedTokens;

      do {
        balancedTokens = parseBalancedTokensInBrackets(builder);
        if (!BALANCED_TOKENS.equals(balancedTokens)) {
          return false;
        }
      } while (!tWRONG_SET.contains(balancedTokens));

      //IDENT
      return ParserUtils.getToken(builder, mIDENT);

    } else {
//      builder.error(GroovyBundle.message("upper.case.ident.or.builtIn.type.or.qualified.type.name.expected"));
      return false;
    }
  }
  private static boolean parseNextTokenInDeclaration(PsiBuilder builder) {
    return ParserUtils.getToken(builder, mIDENT) ||
          ParserUtils.validateToken(builder, TokenSets.BUILT_IN_TYPE) ||
          ParserUtils.getToken(builder, mSTRING_LITERAL);
  }

  private static IElementType parseBalancedTokensInBrackets(PsiBuilder builder) {
    PsiBuilder.Marker btm = builder.mark();

    if (!ParserUtils.getToken(builder, mLBRACK, GroovyBundle.message("lbrack.expected"))) {
      btm.rollbackTo();
      return WRONGWAY;
    }

    if (tWRONG_SET.contains(BalancedTokens.parse(builder))) {
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