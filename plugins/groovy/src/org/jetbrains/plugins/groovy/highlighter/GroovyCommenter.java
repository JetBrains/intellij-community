package org.jetbrains.plugins.groovy.highlighter;

import com.intellij.lang.Commenter;

/**
 * @author Ilya.Sergey
 */

public class GroovyCommenter implements Commenter {
  public String getLineCommentPrefix() {
    return "//";
  }

  public boolean isLineCommentPrefixOnZeroColumn() {
    return false;
  }

  public String getBlockCommentPrefix() {
    return "/*";
  }

  public String getBlockCommentSuffix() {
    return "*/";
  }
    
}
