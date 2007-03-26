package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.blocks;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.auxiliary.Separators;
import org.jetbrains.plugins.groovy.lang.parser.parsing.statements.Statement;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author Ilya.Sergey
 */
public class OpenOrClosableBlock implements GroovyElementTypes {

  /**
   * Parses blocks of both types
   *
   * @param builder
   * @return
   */
  public static GroovyElementType parse(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    if (!ParserUtils.getToken(builder, mLCURLY)) {
      marker.drop();
      return WRONGWAY;
    }
    ParserUtils.getToken(builder, mNLS);
    GroovyElementType result = closableBlockParamsOpt(builder);
    parseBlockBody(builder);
    ParserUtils.getToken(builder, mRCURLY, GroovyBundle.message("rcurly.expected"));
    if (!result.equals(WRONGWAY)) {
      marker.done(CLOSABLE_BLOCK);
      return CLOSABLE_BLOCK;
    } else {
      marker.done(OPEN_BLOCK);
      return OPEN_BLOCK;
    }
  }

  /**
   * Parses only OPEN blocks
   *
   * @param builder
   * @return
   */
  public static GroovyElementType parseOpenBlock(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    if (!ParserUtils.getToken(builder, mLCURLY)) {
      marker.drop();
      return WRONGWAY;
    }
    ParserUtils.getToken(builder, mNLS);
    parseBlockBody(builder);
    ParserUtils.getToken(builder, mRCURLY, GroovyBundle.message("rcurly.expected"));
    marker.done(OPEN_BLOCK);
    return OPEN_BLOCK;
  }


  /**
   * Parses CLOSABLE blocks
   *
   * @param builder
   * @return
   */
  public static GroovyElementType parseClosableBlock(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();
    if (!ParserUtils.getToken(builder, mLCURLY)) {
      marker.drop();
      return WRONGWAY;
    }
    ParserUtils.getToken(builder, mNLS);
    closableBlockParamsOpt(builder);
    parseBlockBody(builder);
    ParserUtils.getToken(builder, mRCURLY, GroovyBundle.message("rcurly.expected"));
    marker.done(CLOSABLE_BLOCK);
    return CLOSABLE_BLOCK;
  }


  private static GroovyElementType closableBlockParamsOpt(PsiBuilder builder) {
    // TODO implement me!
    ParserUtils.getToken(builder, mNLS);
    return WRONGWAY;
  }

  public static GroovyElementType parseBlockBody(PsiBuilder builder) {
    if (mSEMI.equals(builder.getTokenType()) || mNLS.equals(builder.getTokenType())) {
      Separators.parse(builder);
    }

    GroovyElementType result = Statement.parse(builder);
    while (!result.equals(WRONGWAY) &&
            (mSEMI.equals(builder.getTokenType()) || mNLS.equals(builder.getTokenType()))) {
      Separators.parse(builder);
      result = Statement.parse(builder);
      cleanAfterError(builder);
    }
    Separators.parse(builder);
    return BLOCK_BODY;
  }

  /**
   * Rolls marker forward after possible errors
   *
   * @param builder
   */
  private static void cleanAfterError(PsiBuilder builder) {
    int i = 0;
    PsiBuilder.Marker em = builder.mark();
    while (!builder.eof() &&
            !(mNLS.equals(builder.getTokenType()) ||
                    mRCURLY.equals(builder.getTokenType()) ||
                    mSEMI.equals(builder.getTokenType()))
            ) {
      builder.advanceLexer();
      i++;
    }
    if (i > 0) {
      em.error(GroovyBundle.message("separator.or.rcurly.expected"));
    } else {
      em.drop();
    }
  }

}
