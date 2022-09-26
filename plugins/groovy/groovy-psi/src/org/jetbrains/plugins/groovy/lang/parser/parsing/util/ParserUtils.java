// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.parser.parsing.util;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import com.intellij.openapi.util.NlsContexts.ParsingError;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.parser.GrBlockElementType;
import org.jetbrains.plugins.groovy.lang.parser.GrBlockLambdaBodyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GrClosureElementType;

/**
 * Utility classdef, that contains various useful methods for
 * parser needs.
 *
 * @author ilyas
 */
public abstract class ParserUtils {

  /**
   * Auxiliary method for strict token appearance
   *
   * @param builder  current builder
   * @param elem     given element
   * @param errorMsg Message, that displays if element was not found; if errorMsg == null nothing displays
   */
  public static void getToken(PsiBuilder builder, IElementType elem, @ParsingError String errorMsg) {
    if (elem.equals(builder.getTokenType())) {
      builder.advanceLexer();
    }
    else if (errorMsg != null) {
      builder.error(errorMsg);
    }
  }

  /**
   * Auxiliary method for construction like
   * <BNF>
   * token?
   * </BNF>
   * parsing
   *
   * @param builder current builder
   * @param elem    given element
   * @return true if element parsed
   */
  public static boolean getToken(PsiBuilder builder, IElementType elem) {
    if (elem.equals(builder.getTokenType())) {
      builder.advanceLexer();
      return true;
    }
    return false;
  }

  public static boolean lookAhead(PsiBuilder builder, IElementType element1, IElementType element2) {
    if (element1 != builder.getTokenType()) return false;

    Marker rb = builder.mark();
    builder.advanceLexer();
    boolean res = (!builder.eof() && element2 == builder.getTokenType());
    rb.rollbackTo();
    return res;
  }

  // todo: eventually modify the platform to remove this duplication
  public static GrBlockElementType getSwitchAwareBlockElementType(String debugName) {
    return new GrBlockElementType(debugName, true);
  }

  public static GrBlockLambdaBodyElementType getSwitchAwareLambdaBlockElementType(String debugName) {
    return new GrBlockLambdaBodyElementType(debugName, true);
  }

  public static GrClosureElementType getSwitchAwareClosureBlockElementType(String debugName) {
    return new GrClosureElementType(debugName, true);
  }
}
