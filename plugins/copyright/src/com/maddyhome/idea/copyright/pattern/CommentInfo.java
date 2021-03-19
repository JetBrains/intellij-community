// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.maddyhome.idea.copyright.pattern;

import com.intellij.openapi.util.text.StringUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class CommentInfo {
  private final String myText;

  public CommentInfo(String text) {
    myText = text;
  }

  public String getText() {
    return myText;
  }

  public String match(String regexp) {
    return match(regexp, 1, "");
  }

  /**
   * @param regexp   regular expression to match created date in the existing comment
   * @param group    group, where date is expected to be found            
   * @param suffix   if created date was detected, allows to append any postfix e.g. ` - ` so the date range looks nice
   * @return empty string if created date was not found (it's a new comment or regexp didn't match anything in the comment), or
   *         created date from the existing comment followed by the <code>suffix</code>
   */
  public String match(String regexp, int group, String suffix) {
    if (myText == null) {
      return "";
    }
    Matcher matcher = Pattern.compile(".*" + regexp + ".*", Pattern.DOTALL | Pattern.MULTILINE).matcher(myText);
    if (matcher.matches()) {
      String date = matcher.group(group);
      if (!StringUtil.isEmpty(date)) {
        return date + suffix;
      }
    }
    return "";
  }
}