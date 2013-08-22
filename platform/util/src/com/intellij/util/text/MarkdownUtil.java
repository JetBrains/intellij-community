package com.intellij.util.text;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Sergey Simonchik
 */
public class MarkdownUtil {

  private MarkdownUtil() {}

  /**
   * Replaces headers in markdown with HTML.
   * Unfortunately, library used for markdown processing
   * <a href="https://code.google.com/p/markdownj">markdownj</a>
   * doesn't support that.
   */
  public static void replaceHeaders(@NotNull List<String> lines) {
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      int ind = 0;
      while (ind < line.length() && line.charAt(ind) == '#') {
        ind++;
      }
      if (ind < line.length() && line.charAt(ind) == ' ') {
        if (0 < ind && ind <= 9) {
          int endInd = line.length() - 1;
          while (endInd >= 0 && line.charAt(endInd) == '#') {
            endInd--;
          }
          line = line.substring(ind + 1, endInd + 1);
          line = "<h" + ind + ">" + line + "</h" + ind + ">";
          lines.set(i, line);
        }
      }
    }
  }

  /**
   * Removes images in the markdown text.
   * @param lines List of String in markdown format
   */
  public static void removeImages(@NotNull List<String> lines) {
    for (int i = 0; i < lines.size(); i++) {
      String newText = removeAllImages(lines.get(i));
      lines.set(i, newText);
    }
  }

  @NotNull
  private static String removeAllImages(@NotNull String text) {
    int n = text.length();
    List<TextRange> intervals = null;
    int i = 0;
    while (i < n) {
      int imageEndIndex = findImageEndIndexInclusive(text, i);
      if (imageEndIndex != -1) {
        TextRange linkRange = findEnclosingLink(text, i, imageEndIndex);
        if (intervals == null) {
          intervals = new ArrayList<TextRange>(1);
        }
        final TextRange range;
        if (linkRange != null) {
          range = linkRange;
        }
        else {
          range = new TextRange(i, imageEndIndex);
        }
        intervals.add(range);
        i = range.getEndOffset();
      }
      i++;
    }
    if (intervals == null) {
      return text;
    }
    StringBuilder buf = new StringBuilder(text);
    for (int intervalInd = intervals.size() - 1; intervalInd >= 0; intervalInd--) {
      TextRange range = intervals.get(intervalInd);
      buf.delete(range.getStartOffset(), range.getEndOffset() + 1);
    }
    return buf.toString();
  }

  private static int findImageEndIndexInclusive(@NotNull String text, int imageStartIndex) {
    int n = text.length();
    if (text.charAt(imageStartIndex) == '!'
        && imageStartIndex + 1 < n
        && text.charAt(imageStartIndex + 1) == '[') {
      int i = imageStartIndex + 2;
      while (i < n && text.charAt(i) != ']') {
        i++;
      }
      if (i < n && i + 1 < n && text.charAt(i + 1) == '(') {
        i += 2;
        while (i < n && text.charAt(i) != ')') {
          i++;
        }
        if (i < n) {
          return i;
        }
      }
    }
    return -1;
  }

  @Nullable
  private static TextRange findEnclosingLink(@NotNull String text,
                                             int imageStartIndInc,
                                             int imageEndIndInc) {
    int linkStartIndInc = imageStartIndInc - 1;
    if (linkStartIndInc >= 0 && text.charAt(linkStartIndInc) == '[') {
      int n = text.length();
      int i = imageEndIndInc + 1;
      if (text.charAt(i) == ']' && i + 1 < n && text.charAt(i + 1) == '(') {
        i += 2;
        while (i < n && text.charAt(i) != ')') {
          i++;
        }
        if (i < n) {
          return new TextRange(linkStartIndInc, i);
        }
      }
    }
    return null;
  }

  public static void replaceCodeBlock(@NotNull List<String> lines) {
    new CodeBlockProcessor(lines).process();
  }

  private static class CodeBlockProcessor {

    private static final String START_TAGS = "<pre><code>";
    private static final String END_TAGS = "</code></pre>";

    private final List<String> myLines;

    private boolean myGlobalCodeBlockStarted = false;
    private boolean myCodeBlockStarted = false;

    private CodeBlockProcessor(@NotNull List<String> lines) {
      myLines = lines;
    }

    public void process() {
      for (int i = 0; i < myLines.size(); i++) {
        final String line = myLines.get(i);
        if (line.startsWith("```")) {
          finishCodeBlock(i - 1);
          myGlobalCodeBlockStarted = !myGlobalCodeBlockStarted;
          String out = myGlobalCodeBlockStarted ? START_TAGS : END_TAGS;
          myLines.set(i, out);
        }
        else {
          if (!myGlobalCodeBlockStarted) {
            handleLocalCodeBlock(i, line);
          }
        }
      }
      finishCodeBlock(myLines.size() - 1);
    }

    private void handleLocalCodeBlock(int ind, @NotNull String line) {
      boolean codeBlock = false;
      if (line.startsWith("    ")) {
        line = line.substring(4);
        codeBlock = true;
      }
      else if (line.startsWith("\t")) {
        line = line.substring(1);
        codeBlock = true;
      }

      if (!myCodeBlockStarted) {
        if (codeBlock) {
          myCodeBlockStarted = true;
          myLines.set(ind, START_TAGS + line);
        }
      }
      else {
        if (codeBlock) {
          myLines.set(ind, line);
        }
        else {
          finishCodeBlock(ind - 1);
        }
      }
    }

    private void finishCodeBlock(int lastCodeBlockLineInd) {
      if (myCodeBlockStarted) {
        myLines.set(lastCodeBlockLineInd, myLines.get(lastCodeBlockLineInd) + END_TAGS);
        myCodeBlockStarted = false;
      }
    }
  }

  public static void generateLists(@NotNull List<String> lines) {
    new ListItemProcessor(lines).process();
  }

  private static class ListItemProcessor {

    private final List<String> myLines;

    private boolean myInsideBlockQuote = false;
    private ListItem myFirstListItem = null;
    private int myLastListItemLineInd = -1;

    private ListItemProcessor(@NotNull List<String> lines) {
      myLines = lines;
    }

    public void process() {
      for (int i = 0; i < myLines.size(); i++) {
        final String line = myLines.get(i);
        if (line.startsWith("```")) {
          myInsideBlockQuote = !myInsideBlockQuote;
        }
        if (!myInsideBlockQuote) {
          handle(i, line);
        }
      }
      finishLastListItem(true);
    }

    private void handle(int ind, @NotNull String line) {
      ListItem listItem = toListItem(line);
      if (listItem != null) {
        finishLastListItem(false);
        String out = "<li>" + listItem.getBody();
        if (myFirstListItem == null) {
          myFirstListItem = listItem;
          if (listItem.isUnordered()) {
            out = "<ul>" + out;
          }
          else {
            out = "<ol>" + out;
          }
        }
        myLines.set(ind, out);
        myLastListItemLineInd = ind;
      }
      else if (myFirstListItem != null &&
          !line.isEmpty() &&
          !StringUtil.isEmptyOrSpaces(line)) {
        if (ind - 1 >= 0
            && StringUtil.isEmptyOrSpaces(myLines.get(ind - 1))
            && !Character.isWhitespace(line.charAt(0))) {
          finishLastListItem(true);
        }
        else {
          String m = StringUtil.trimLeading(line);
          myLines.set(ind, m);
          myLastListItemLineInd = ind;
        }
      }
    }

    private void finishLastListItem(boolean finishList) {
      if (myLastListItemLineInd != -1) {
        String l = myLines.get(myLastListItemLineInd);
        l += "</li>";
        if (finishList) {
          if (myFirstListItem.isUnordered()) {
            l += "</ul>";
          }
          else {
            l += "</ol>";
          }
          myFirstListItem = null;
        }
        myLines.set(myLastListItemLineInd, l);
        myLastListItemLineInd = -1;
      }
    }
  }

  @Nullable
  private static ListItem toListItem(@NotNull String line) {
    line = StringUtil.trimLeading(line);
    if (line.length() >= 2) {
      char firstChar = line.charAt(0);
      char secondChar = line.charAt(1);
      if (firstChar == '*' || firstChar == '+' || firstChar == '-') {
        if (Character.isWhitespace(secondChar)) {
          return new ListItem(true, StringUtil.trimLeading(line.substring(1)));
        }
      }
    }
    int i = 0;
    while (i < line.length() && Character.isDigit(line.charAt(i))) {
      i++;
    }
    if (i > 0 && i < line.length() - 1) {
      if (line.charAt(i) == '.' && Character.isWhitespace(line.charAt(i + 1))) {
        return new ListItem(false, StringUtil.trimLeading(line.substring(i + 1)));
      }
    }
    return null;
  }

  private static class ListItem {

    private final boolean myUnordered;
    private final String myBody;

    private ListItem(boolean unordered, @NotNull String body) {
      myUnordered = unordered;
      myBody = body;
    }

    private boolean isUnordered() {
      return myUnordered;
    }

    @NotNull
    private String getBody() {
      return myBody;
    }
  }

}
