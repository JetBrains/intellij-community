package com.intellij.cvsSupport2.cvshandlers;

import com.intellij.util.PatternUtil;
import com.intellij.util.PatternUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NonNls;

/**
 * author: lesya
 */
public class CvsMessagePattern {
  private final Pattern myPattern;
  private final int myFileNameGroup;

  public CvsMessagePattern(@NonNls String[] groups, int fileNameGroup) {
    myFileNameGroup = fileNameGroup;
    String regex = createRegex(groups);
    myPattern = Pattern.compile(regex);
  }

  private String createRegex(@NonNls String[] groups) {
    StringBuffer result = new StringBuffer();
    for (int i = 0; i < groups.length; i++) {
      String group = groups[i];
      result.append("(");
      result.append(PatternUtil.convertToRegex(group));
      result.append(")");
    }
    return result.toString();
  }

  public boolean matches(String string) {
    return myPattern.matcher(string).matches();
  }

  public CvsMessagePattern(String[] pattern) {
    this(pattern, -1);
  }


  public CvsMessagePattern(@NonNls String pattern) {
    this(new String[]{pattern});
  }

  public String getRelativeFileName(String message) {
    if (myFileNameGroup < 0) return null;
    Matcher matcher = myPattern.matcher(message);
    if (matcher.matches()) {
      return matcher.group(myFileNameGroup);
    }
    {
      return null;
    }

  }
}
