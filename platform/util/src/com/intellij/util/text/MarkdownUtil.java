package com.intellij.util.text;

import com.intellij.openapi.util.TextRange;
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
    boolean insideQuotedBlock = false;
    boolean started = false;
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      boolean codeBlock = false;
      if (line.startsWith("```")) {
        insideQuotedBlock = !insideQuotedBlock;
        if (insideQuotedBlock) {
          if (started) {
            String lastCodeBlockLine = lines.get(lines.size() - 1);
            lastCodeBlockLine += "</code></pre>";
            lines.set(lines.size() - 1, lastCodeBlockLine);
            started = false;
          }
          line = "<pre><code>";
        }
        else {
          line = "</code></pre>";
        }
      }
      if (!insideQuotedBlock) {
        if (line.startsWith("    ")) {
          line = line.substring(4);
          codeBlock = true;
        }
        else if (line.startsWith("\t")) {
          line = line.substring(1);
          codeBlock = true;
        }
        if (!started && codeBlock) {
          started = true;
          line = "<pre><code>" + line;
        }
      }
      lines.set(i, line);
      if (!insideQuotedBlock) {
        if (started && !codeBlock) {
          String lastCodeBlockLine = lines.get(i - 1);
          lastCodeBlockLine += "</code></pre>";
          lines.set(i - 1, lastCodeBlockLine);
          started = false;
        }
      }
    }
    if (started) {
      String lastCodeBlockLine = lines.get(lines.size() - 1);
      lastCodeBlockLine += "</code></pre>";
      lines.set(lines.size() - 1, lastCodeBlockLine);
    }
  }

}
