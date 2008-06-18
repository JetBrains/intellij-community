package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import org.jetbrains.annotations.Nullable;

/**
 * @author ilyas
 */
public class GroovyReferenceCharFilter extends CharFilter {
  @Nullable
  public Result acceptChar(char c, int pefixLength, Lookup lookup) {
    if (Character.isJavaIdentifierPart(c) || c == '\'') return Result.ADD_TO_PREFIX;
    else if (c == '\n' || c == '\t') {
      return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
    }
    return Result.HIDE_LOOKUP;
  }

}
