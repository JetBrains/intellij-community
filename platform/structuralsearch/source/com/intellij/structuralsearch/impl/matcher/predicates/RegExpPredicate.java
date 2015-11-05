package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.psi.*;
import com.intellij.structuralsearch.MalformedPatternException;
import com.intellij.structuralsearch.SSRBundle;
import com.intellij.structuralsearch.StructuralSearchProfile;
import com.intellij.structuralsearch.StructuralSearchUtil;
import com.intellij.structuralsearch.impl.matcher.MatchContext;
import com.intellij.structuralsearch.impl.matcher.MatchResultImpl;
import com.intellij.structuralsearch.impl.matcher.MatchUtils;
import com.intellij.structuralsearch.impl.matcher.handlers.MatchPredicate;
import com.intellij.structuralsearch.plugin.util.SmartPsiPointer;
import org.jetbrains.annotations.NonNls;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Root of handlers for pattern node matching. Handles simpliest type of the match.
 */
public final class RegExpPredicate extends MatchPredicate {
  private Pattern pattern;
  private final String baseHandlerName;
  private boolean simpleString;
  private final boolean couldBeOptimized;
  private final String regexp;
  private final boolean caseSensitive;
  private boolean multiline;
  private final boolean wholeWords;
  private final boolean target;
  private NodeTextGenerator myNodeTextGenerator;

  public interface NodeTextGenerator {
    String getText(PsiElement element);
  }

  public RegExpPredicate(final String regexp, final boolean caseSensitive, final String _baseHandlerName, boolean _wholeWords, boolean _target) {
    couldBeOptimized = containsRegExp(regexp);
    if (!_wholeWords) {
      simpleString = couldBeOptimized;
    }

    this.regexp = regexp;
    this.caseSensitive = caseSensitive;
    this.wholeWords = _wholeWords;
    baseHandlerName = _baseHandlerName;

    if (!simpleString) {
      compilePattern();
    }
    target = _target;
  }

  private static boolean containsRegExp(final String regexp) {
    for(int i=0;i<regexp.length();++i) {
      if(MatchUtils.SPECIAL_CHARS.indexOf(regexp.charAt(i))!=-1) {
        return false;
      }
    }

    return true;
  }

  private void compilePattern() {
    try {
      @NonNls String realRegexp = regexp;
      if (wholeWords) {
        realRegexp = ".*?\\b(?:" + realRegexp + ")\\b.*?";
      }

      pattern = Pattern.compile(
        realRegexp,
        (caseSensitive ? 0: Pattern.CASE_INSENSITIVE) | (multiline ? Pattern.DOTALL:0)
      );
    } catch(PatternSyntaxException ex) {
      throw new MalformedPatternException(SSRBundle.message("error.incorrect.regexp.constraint", regexp, baseHandlerName));
    }
  }

  public boolean couldBeOptimized() {
    return couldBeOptimized;
  }

  public String getRegExp() {
    return regexp;
  }

  /**
   * Attempts to match given handler node against given node.
   * @param matchedNode for matching
   * @param context of the matching
   * @return true if matching was successfull and false otherwise
   */
  public boolean match(PsiElement node,PsiElement matchedNode, int start, int end, MatchContext context) {
    if (matchedNode==null) return false;
    String text;

    text = myNodeTextGenerator != null ? myNodeTextGenerator.getText(matchedNode) : getMeaningfulText(matchedNode);

    boolean result = doMatch(text, start, end, context, matchedNode);

    if (!result) {

      matchedNode = StructuralSearchUtil.getParentIfIdentifier(matchedNode);

      String alternativeText = context.getPattern().getAlternativeTextToMatch(matchedNode, text);
      if (alternativeText != null) {
        result = doMatch(alternativeText, start, end, context, matchedNode);
      }
    }

    return result;
  }

  public static String getMeaningfulText(PsiElement matchedNode) {
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByPsiElement(matchedNode);
    return profile != null ? profile.getMeaningfulText(matchedNode) : matchedNode.getText();
  }

  public boolean match(PsiElement patternNode, PsiElement matchedNode, MatchContext context) {
    return match(patternNode,matchedNode,0,-1,context);
  }

  boolean doMatch(String text, MatchContext context, PsiElement matchedElement) {
    return doMatch(text,0,-1,context, matchedElement);
  }

  boolean doMatch(String text, int from, int end, MatchContext context,PsiElement matchedElement) {
    if (from > 0 || end != -1) {
      text = text.substring(from, end == -1 || end >= text.length() ? text.length():end);
    }

    if (simpleString) {
      return (caseSensitive)?text.equals(regexp):text.equalsIgnoreCase(regexp);
    }

    if(!multiline && text.contains("\n")) setMultiline(true);
    final Matcher matcher = pattern.matcher(text);

    if (matcher.matches()) {
      for (int i=1;i<=matcher.groupCount();++i) {
        context.getResult().addSon(
          new MatchResultImpl(
            baseHandlerName + "_" + i,
            matcher.group(i),
            new SmartPsiPointer(matchedElement),
            matcher.start(i),
            matcher.end(i),
            target
          )
        );
      }
      return true;
    } else {
      return false;
    }
  }


  public void setNodeTextGenerator(final NodeTextGenerator nodeTextGenerator) {
    myNodeTextGenerator = nodeTextGenerator;
  }

  public void setMultiline(boolean b) {
    multiline = b;
    compilePattern();
  }

  public boolean isWholeWords() {
    return wholeWords;
  }
}
