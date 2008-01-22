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

package org.jetbrains.plugins.groovy.lang.parser.parsing.types;

import com.intellij.lang.PsiBuilder;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 28.03.2007
 */
public class TypeArguments implements GroovyElementTypes {
  public static boolean parse(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();

    if (!ParserUtils.getToken(builder, mLT)) {
      marker.rollbackTo();
      return false;
    }

    ParserUtils.getToken(builder, mNLS);

    if (!TypeArgument.parse(builder)) {
      marker.rollbackTo();
      return false;
    }

    while (ParserUtils.getToken(builder, mCOMMA)) {
      ParserUtils.getToken(builder, mNLS);

      if (!TypeArgument.parse(builder)) {
        builder.error("type.argument.expected");
      }
    }

    PsiBuilder.Marker rb = builder.mark();
    ParserUtils.getToken(builder, mNLS);

    if (ParserUtils.getToken(builder, mGT)) {
      rb.drop();
      ParserUtils.getToken(builder, mNLS);
    } else {
      rb.rollbackTo();
      builder.error(GroovyBundle.message("gt.expected"));
    }

    marker.done(TYPE_ARGUMENTS);
    return true;
  }
}
