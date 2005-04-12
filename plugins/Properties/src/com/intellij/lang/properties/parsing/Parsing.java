/**
 * @author Alexey
 */
package com.intellij.lang.properties.parsing;

import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.lang.properties.PropertiesElementTypes;
import com.intellij.lang.properties.PropertiesTokenTypes;

public class Parsing {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.properties.parsing.Parsing");

  public static void parseProperty(PsiBuilder builder) {
    if (builder.getTokenType() == PropertiesTokenTypes.KEY_CHARACTERS) {
      final PsiBuilder.Marker prop = builder.mark();

      parseKey(builder);
      if (builder.getTokenType() == PropertiesTokenTypes.KEY_VALUE_SEPARATOR) {
        parseKeyValueSeparator(builder);
        parseValue(builder);
      }
      prop.done(PropertiesElementTypes.PROPERTY);
    }
    else {
      builder.advanceLexer();
      builder.error("property key expected");
    }
  }

  private static void parseKeyValueSeparator(final PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PropertiesTokenTypes.KEY_VALUE_SEPARATOR);
    //final PsiBuilder.Marker separator = builder.mark();
    builder.advanceLexer();
    //separator.done(PropertiesElementTypes.KEY_VALUE_SEPARATOR);
  }

  private static void parseValue(final PsiBuilder builder) {
    //final PsiBuilder.Marker value = builder.mark();
    if (builder.getTokenType() == PropertiesTokenTypes.VALUE_CHARACTERS) {
      builder.advanceLexer();
    }
    //value.done(PropertiesElementTypes.VALUE);
  }

  private static void parseKey(final PsiBuilder builder) {
    LOG.assertTrue(builder.getTokenType() == PropertiesTokenTypes.KEY_CHARACTERS);
    //final PsiBuilder.Marker key = builder.mark();
    builder.advanceLexer();
    //key.done(PropertiesElementTypes.KEY);
  }
}