// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.utils;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.markdown4j.Markdown4jProcessor;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TODO move to contrib/markdown/lib/intellij-markdown.jar
 */
public final class HtmlMarkdownUtils {

  private static final Logger LOG = Logger.getInstance(HtmlMarkdownUtils.class);

  private static final Pattern TAG_START_OR_CLOSE_PATTERN = Pattern.compile("(<)/?(\\w+)[> ]");
  private static final Pattern SPLIT_BY_LINE_PATTERN = Pattern.compile("\n|\r|\r\n");
  private static final String HTML_CODE_START = "<code>";
  private static final String HTML_CODE_END = "</code>";
  private static final String FENCED_CODE_BLOCK = "```";
  private static final String INLINE_CODE_BLOCK = "``";

  // Final adjustments to make PhpDoc look more readable
  // Final adjustments to make PhpDoc look more readable
  private static final Map<String, String> HTML_DOC_SUBSTITUTIONS = new HashMap<>();
  public static final String BR_TAG_AFTER_MARKDOWN_PROCESSING = "<br  />";

  static {
    HTML_DOC_SUBSTITUTIONS.put("<pre><code>", "<pre>");
    HTML_DOC_SUBSTITUTIONS.put("</code></pre>", "</pre>");
    HTML_DOC_SUBSTITUTIONS.put("<em>", "<i>");
    HTML_DOC_SUBSTITUTIONS.put("</em>", "</i>");
    HTML_DOC_SUBSTITUTIONS.put("<strong>", "<b>");
    HTML_DOC_SUBSTITUTIONS.put("</strong>", "</b>");
    HTML_DOC_SUBSTITUTIONS.put(": //", "://"); // Fix URL
  }

  private static final Set<String> ACCEPTABLE_TAGS = ContainerUtil.immutableSet("p", "i", "code", "ul", "h1", "h2", "h3", "h4", "h5", "h6",
                                                                                "li", "blockquote", "ol", "b", "a", "tt", "tt", "pre", "tr", "th",
                                                                                "td", "table", "strong", "em", "u", "dl", "dd", "dt");

  private HtmlMarkdownUtils() {
  }

  @Contract(pure = true)
  public static @Nullable String toHtml(@NotNull String markdownText) {
    return toHtml(markdownText, true);
  }

  private static @NotNull String getBorder() {
    return "margin: 0; border: 1px solid; border-color: #" + ColorUtil
      .toHex(UIUtil.getTooltipSeparatorColor()) + "; border-spacing: 0; border-collapse: collapse;vertical-align: baseline;";
  }

  @Contract(pure = true)
  public static @Nullable String toHtml(@NotNull String markdownText, boolean convertTagCodeBlocks) {
    String[] lines = SPLIT_BY_LINE_PATTERN.split(markdownText);
    List<String> processedLines = new ArrayList<>(lines.length);
    boolean isInCode = false;
    boolean isInTable = false;
    List<String> tableFormats = null;
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      String processedLine = StringUtil.trimTrailing(line);
      processedLine = StringUtil.trimStart(processedLine, " ");

      int count = StringUtil.getOccurrenceCount(processedLine, FENCED_CODE_BLOCK);
      if (count > 0) {
        //noinspection SimplifiableConditionalExpression
        isInCode = count % 2 == 0 ? isInCode : !isInCode;
      }
      else {
        if (convertTagCodeBlocks) {
          if (isInCode) {
            if (processedLine.startsWith(HTML_CODE_END)) {
              processedLines.add(FENCED_CODE_BLOCK);
              processedLine = StringUtil.trimStart(processedLine, HTML_CODE_END);
              isInCode = false;
            }
          }
          else {
            boolean codeStart = false;
            if (processedLine.endsWith(HTML_CODE_START)) {
              codeStart = true;
              processedLine = StringUtil.trimEnd(processedLine, HTML_CODE_START);
            }
            processedLine = processedLine
              .replace("<pre>", FENCED_CODE_BLOCK).replace("</pre>", FENCED_CODE_BLOCK)
              .replace(HTML_CODE_START, INLINE_CODE_BLOCK).replace(HTML_CODE_END, INLINE_CODE_BLOCK);
            if (codeStart) {
              processedLines.add(processedLine);
              processedLine = FENCED_CODE_BLOCK;
              isInCode = true;
            }
          }
        }
        else {
          int tableDelimiterIndex = processedLine.indexOf('|');
          if (tableDelimiterIndex != -1) {
            if (!isInTable) {
              if (i + 1 < lines.length) {
                tableFormats = parseTableFormats(splitTableCols(lines[i + 1]));
              }
            }
            // create table only if we've successfully read the formats line
            if (!ContainerUtil.isEmpty(tableFormats)) {
              List<String> parts = splitTableCols(processedLine);
              if (isTableHeaderSeparator(parts)) continue;
              processedLine = getProcessedRow(isInTable, parts, tableFormats);
              if (!isInTable) processedLine = "<table style=\"border: 0px;\" cellspacing=\"0\">" + processedLine;
              isInTable = true;
            }
          }
          else {
            if (isInTable) processedLine += "</table>";
            isInTable = false;
            tableFormats = null;
          }
          List<TextRange> ranges = getInlineCodeBlocks(processedLine);
          processedLine = isInCode ? processedLine : replaceProhibitedTags(StringUtil.trimLeading(processedLine), ranges);
        }
      }
      processedLines.add(processedLine);
    }
    String normalizedMarkdown = StringUtil.join(processedLines, "\n");
    if (isInTable) normalizedMarkdown += "</table>"; //NON-NLS
    String html = convert(normalizedMarkdown);

    return adjustHtml(html);
  }

  private static @Nullable List<String> parseTableFormats(@NotNull List<String> cols) {
    List<String> formats = new ArrayList<>();
    for (String col : cols) {
      if (!isHeaderSeparator(col)) return null;
      formats.add(parseFormat(col.trim()));
    }
    return formats;
  }

  private static boolean isTableHeaderSeparator(@NotNull List<String> parts) {
    return parts.stream().allMatch(HtmlMarkdownUtils::isHeaderSeparator);
  }

  private static boolean isHeaderSeparator(@NotNull String s) {
    return StringUtil.trimEnd(StringUtil.trimStart(s.trim(), ":"), ":").chars().allMatch(sx -> sx == '-');
  }

  private static @NotNull List<String> splitTableCols(@NotNull String processedLine) {
    List<String> parts = new ArrayList<>(StringUtil.split(processedLine, "|"));
    if (parts.isEmpty()) return parts;
    if (StringUtil.isEmptyOrSpaces(parts.get(0))) parts.remove(0);
    if (!parts.isEmpty() && StringUtil.isEmptyOrSpaces(parts.get(parts.size() - 1))) parts.remove(parts.size() - 1);
    return parts;
  }

  private static @NotNull String getProcessedRow(boolean isInTable,
                                                 @NotNull List<String> parts,
                                                 @Nullable List<String> tableFormats) {
    String openingTagStart = isInTable
                             ? "<td style=\"" + getBorder() + "\" "
                             : "<th style=\"" + getBorder() + "\" ";
    String closingTag = isInTable ? "</td>" : "</th>";
    StringBuilder resultBuilder = new StringBuilder("<tr style=\"" + getBorder() + "\">" + openingTagStart);
    resultBuilder.append("align=\"").append(getAlign(0, tableFormats)).append("\">");
    for (int i = 0; i < parts.size(); i++) {
      if (i > 0) {
        resultBuilder.append(closingTag).append(openingTagStart).append("align=\"").append(getAlign(i, tableFormats)).append("\">");
      }
      resultBuilder.append(convert(parts.get(i).trim()));
    }
    resultBuilder.append(closingTag).append("</tr>");
    return resultBuilder.toString();
  }

  private static @NotNull String getAlign(int index, @Nullable List<String> formats) {
    return formats == null || index >= formats.size() ? "left" : formats.get(index);
  }

  private static @NotNull String parseFormat(@NotNull String format) {
    if (format.length() <= 1) return "left";
    char c0 = format.charAt(0);
    char cE = format.charAt(format.length() - 1);
    return c0 == ':' && cE == ':' ? "center" : cE == ':' ? "right" : "left";
  }

  private static @NotNull List<TextRange> getInlineCodeBlocks(@NotNull String processingLine) {
    int next = 0;
    List<TextRange> ranges = new ArrayList<>();
    int length = processingLine.length();
    while (next >= 0 && next < length) {
      int startQuote = processingLine.indexOf('`', next);
      if (startQuote < 0 || length <= startQuote + 1) break;
      char nextChar = processingLine.charAt(startQuote + 1);
      int offset = nextChar == '`' ? 2 : 1;
      if (length <= startQuote + offset) break;
      int endQuote = processingLine.indexOf('`', startQuote + offset);
      if (endQuote <= 0) break;
      ranges.add(new TextRange(startQuote, endQuote));
      next = endQuote + offset;
    }

    return ranges;
  }

  private static @Nullable @NlsSafe String convert(@NotNull @Nls String text) {
    try {
      return new Markdown4jProcessor().process(text);
    }
    catch (Exception e) {
      LOG.warn(e.getMessage(), e);
      return null;
    }
  }

  public static @NotNull String replaceProhibitedTags(@NotNull String line) {
    return replaceProhibitedTags(line, ContainerUtil.emptyList());
  }

  private static @NotNull String replaceProhibitedTags(@NotNull String line, @NotNull List<? extends TextRange> skipRanges) {
    IntList list = collectProhibitedTagOffsets(line, skipRanges);
    return list.isEmpty() ? line : escapeTagsStart(line, list);
  }

  private static @NotNull String escapeTagsStart(@NotNull String line, @NotNull IntList orderedOffsetList) {
    StringBuilder builder = new StringBuilder(line);
    for (int i = orderedOffsetList.size() - 1; i >= 0; i--) {
      var el = orderedOffsetList.getInt(i);
      builder.replace(el, el + 1, "&lt;");
    }
    return builder.toString();
  }

  private static @NotNull IntList collectProhibitedTagOffsets(@NotNull String line, @NotNull List<? extends TextRange> skipRanges) {
    Matcher matcher = TAG_START_OR_CLOSE_PATTERN.matcher(line);
    IntList list = new IntArrayList();

    l:
    while (matcher.find()) {
      final String tagName = matcher.group(2);

      if (ACCEPTABLE_TAGS.contains(StringUtil.toLowerCase(tagName))) continue;

      int startOfTag = matcher.start(2);
      for (TextRange range : skipRanges) {
        if (range.contains(startOfTag)) {
          continue l;
        }
      }

      list.add(matcher.start(1));
    }
    return list;
  }

  @Contract(pure = true)
  public static @Nullable String adjustHtml(@Nullable String html) {
    if (html == null) return null;
    String str = html;
    for (Map.Entry<String, String> entry : HTML_DOC_SUBSTITUTIONS.entrySet()) {
      str = str.replace(entry.getKey(), entry.getValue());
    }
    str = str.replace(BR_TAG_AFTER_MARKDOWN_PROCESSING, "");

    return str.trim();
  }
}
