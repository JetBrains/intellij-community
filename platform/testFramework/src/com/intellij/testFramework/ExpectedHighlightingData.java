/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
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

/**
 * @author cdr
 */
package com.intellij.testFramework;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.Function;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import junit.framework.Assert;
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExpectedHighlightingData {
  private static final Logger LOG = Logger.getInstance("#com.intellij.testFramework.ExpectedHighlightingData");

  @NonNls private static final String ERROR_MARKER = "error";
  @NonNls private static final String WARNING_MARKER = "warning";
  @NonNls private static final String INFORMATION_MARKER = "weak_warning";
  @NonNls private static final String INFO_MARKER = "info";
  @NonNls private static final String END_LINE_HIGHLIGHT_MARKER = "EOLError";
  @NonNls private static final String END_LINE_WARNING_MARKER = "EOLWarning";
  @NonNls private static final String LINE_MARKER = "lineMarker";

  private final PsiFile myFile;
  @NonNls private static final String ANY_TEXT = "*";
  String myText;

  public static class ExpectedHighlightingSet {
    private final boolean endOfLine;
    final boolean enabled;
    final Set<HighlightInfo> infos;
    final HighlightInfoType defaultErrorType;
    final HighlightSeverity severity;

    public ExpectedHighlightingSet(HighlightInfoType defaultErrorType, HighlightSeverity severity, boolean endOfLine, boolean enabled) {
      this.endOfLine = endOfLine;
      this.enabled = enabled;
      infos = new THashSet<HighlightInfo>();
      this.defaultErrorType = defaultErrorType;
      this.severity = severity;
    }
  }
  @SuppressWarnings("WeakerAccess")
  protected final Map<String,ExpectedHighlightingSet> highlightingTypes;
  private final Map<RangeMarker, LineMarkerInfo> lineMarkerInfos = new THashMap<RangeMarker, LineMarkerInfo>();

  public ExpectedHighlightingData(Document document,boolean checkWarnings, boolean checkInfos) {
    this(document, checkWarnings, false, checkInfos);
  }

  public ExpectedHighlightingData(Document document,
                                  boolean checkWarnings,
                                  boolean checkWeakWarnings,
                                  boolean checkInfos) {
    this(document, checkWarnings, checkWeakWarnings, checkInfos, null);
  }

  public ExpectedHighlightingData(Document document,
                                  boolean checkWarnings,
                                  boolean checkWeakWarnings,
                                  boolean checkInfos,
                                  PsiFile file) {
    myFile = file;
    myText = document.getText();
    highlightingTypes = new LinkedHashMap<String,ExpectedHighlightingSet>();
    highlightingTypes.put(ERROR_MARKER, new ExpectedHighlightingSet(HighlightInfoType.ERROR, HighlightSeverity.ERROR, false, true));
    highlightingTypes.put(WARNING_MARKER, new ExpectedHighlightingSet(HighlightInfoType.WARNING, HighlightSeverity.WARNING, false, checkWarnings));
    highlightingTypes.put(INFORMATION_MARKER, new ExpectedHighlightingSet(HighlightInfoType.INFO, HighlightSeverity.INFO, false, checkWeakWarnings));
    highlightingTypes.put("inject", new ExpectedHighlightingSet(HighlightInfoType.INJECTED_LANGUAGE_FRAGMENT, HighlightInfoType.INJECTED_FRAGMENT_SEVERITY, false, checkInfos));
    highlightingTypes.put(INFO_MARKER, new ExpectedHighlightingSet(HighlightInfoType.TODO, HighlightSeverity.INFORMATION, false, checkInfos));
    for (SeveritiesProvider provider : Extensions.getExtensions(SeveritiesProvider.EP_NAME)) {
      for (HighlightInfoType type : provider.getSeveritiesHighlightInfoTypes()) {
        final HighlightSeverity severity = type.getSeverity(null);
        highlightingTypes.put(severity.toString(), new ExpectedHighlightingSet(type, severity , false, true));
      }
    }
    highlightingTypes.put(END_LINE_HIGHLIGHT_MARKER, new ExpectedHighlightingSet(HighlightInfoType.ERROR, HighlightSeverity.ERROR, true, true));
    highlightingTypes.put(END_LINE_WARNING_MARKER, new ExpectedHighlightingSet(HighlightInfoType.WARNING, HighlightSeverity.WARNING, true, checkWarnings));
    initAdditionalHighlightingTypes();
    extractExpectedLineMarkerSet(document);
    extractExpectedHighlightsSet(document);
    refreshLineMarkers();
  }

  private void refreshLineMarkers() {
    for (Map.Entry<RangeMarker, LineMarkerInfo> entry : lineMarkerInfos.entrySet()) {
      RangeMarker rangeMarker = entry.getKey();
      int startOffset = rangeMarker.getStartOffset();
      int endOffset = rangeMarker.getEndOffset();
      final LineMarkerInfo value = entry.getValue();
      LineMarkerInfo markerInfo = new LineMarkerInfo<PsiElement>(value.getElement(), new TextRange(startOffset,endOffset), null, value.updatePass, new Function<PsiElement,String>() {
        public String fun(PsiElement psiElement) {
          return value.getLineMarkerTooltip();
        }
      }, null, GutterIconRenderer.Alignment.RIGHT);
      entry.setValue(markerInfo);
    }
  }

  private void extractExpectedLineMarkerSet(Document document) {
    String text = document.getText();

    @NonNls String pat = ".*?((<" + LINE_MARKER + ")(?: descr=\"((?:[^\"\\\\]|\\\\\")*)\")?>)(.*)";
    final Pattern p = Pattern.compile(pat, Pattern.DOTALL);
    final Pattern pat2 = Pattern.compile("(.*?)(</" + LINE_MARKER + ">)(.*)", Pattern.DOTALL);

    for (; ;) {
      Matcher m = p.matcher(text);
      if (!m.matches()) break;
      int startOffset = m.start(1);
      final String descr = m.group(3) != null ? m.group(3): ANY_TEXT;
      String rest = m.group(4);

      document.replaceString(startOffset, m.end(1), "");

      final Matcher matcher2 = pat2.matcher(rest);
      LOG.assertTrue(matcher2.matches(), "Cannot find closing </" + LINE_MARKER + ">");
      String content = matcher2.group(1);
      int endOffset = startOffset + matcher2.start(3);
      String endTag = matcher2.group(2);

      document.replaceString(startOffset, endOffset, content);
      endOffset -= endTag.length();

      LineMarkerInfo markerInfo = new LineMarkerInfo<PsiElement>(myFile, new TextRange(startOffset,endOffset), null, Pass.LINE_MARKERS, new Function<PsiElement,String>() {
        public String fun(PsiElement psiElement) {
          return descr;
        }
      }, null, GutterIconRenderer.Alignment.RIGHT);

      lineMarkerInfos.put(document.createRangeMarker(startOffset, endOffset), markerInfo);
      text = document.getText();
    }
  }

  /**
   * Override in order to register special highlighting
   */
  protected void initAdditionalHighlightingTypes() {}

  /**
   * remove highlights (bounded with <marker>...</marker>) from test case file
   * @param document document to process
   */
  private void extractExpectedHighlightsSet(final Document document) {
    final String text = document.getText();

    final Set<String> markers = highlightingTypes.keySet();
    final String typesRx = "(?:" + StringUtil.join(markers, ")|(?:") + ")";
    final String openingTagRx = "<(" + typesRx + ")" +
                                "(?:\\s+descr=\"((?:[^\"\\\\]|\\\\\"|\\\\\\\\\")*)\")?" +
                                "(?:\\s+type=\"([0-9A-Z_]+)\")?" +
                                "(?:\\s+foreground=\"([0-9xa-f]+)\")?" +
                                "(?:\\s+background=\"([0-9xa-f]+)\")?" +
                                "(?:\\s+effectcolor=\"([0-9xa-f]+)\")?" +
                                "(?:\\s+effecttype=\"([A-Z]+)\")?" +
                                "(?:\\s+fonttype=\"([0-9]+)\")?" +
                                "(/)?>";

    final Matcher matcher = Pattern.compile(openingTagRx).matcher(text);
    int pos = 0;
    final Ref<Integer> textOffset = Ref.create(0);
    while (matcher.find(pos)) {
      textOffset.set(textOffset.get() + (matcher.start() - pos));
      pos = extractExpectedHighlight(matcher, text, document, textOffset);
    }
  }

  private int extractExpectedHighlight(final Matcher matcher, final String text, final Document document, final Ref<Integer> textOffset) {
    final int toContinueFrom;

    document.deleteString(textOffset.get(), textOffset.get() + (matcher.end() - matcher.start()));

    int groupIdx = 1;
    final String marker = matcher.group(groupIdx++);
    @NonNls String descr = matcher.group(groupIdx++);
    final String typeString = matcher.group(groupIdx++);
    final String foregroundColor = matcher.group(groupIdx++);
    final String backgroundColor = matcher.group(groupIdx++);
    final String effectColor = matcher.group(groupIdx++);
    final String effectType = matcher.group(groupIdx++);
    final String fontType = matcher.group(groupIdx++);
    final boolean closed = matcher.group(groupIdx) != null;

    if (descr == null) {
      descr = ANY_TEXT;  // no descr means any string by default
    }
    else if (descr.equals("null")) {
      descr = null;  // explicit "null" descr
    }
    if (descr != null) {
      descr = descr.replaceAll("\\\\\\\\\"", "\"");  // replace: \\" to ", doesn't check symbol before sequence \\"
    }

    HighlightInfoType type = WHATEVER;
    if (typeString != null) {
      try {
        Field field = HighlightInfoType.class.getField(typeString);
        type = (HighlightInfoType)field.get(null);
      }
      catch (Exception ignore) {
      }
      LOG.assertTrue(type != null, "Wrong highlight type: " + typeString);
    }

    TextAttributes forcedAttributes = null;
    if (foregroundColor != null) {
      forcedAttributes = new TextAttributes(Color.decode(foregroundColor), Color.decode(backgroundColor), Color.decode(effectColor),
                                            EffectType.valueOf(effectType), Integer.parseInt(fontType));
    }

    final int rangeStart = textOffset.get();
    if (closed) {
      toContinueFrom = matcher.end();
    }
    else {
      int pos = matcher.end();
      final Matcher closingTagMatcher = Pattern.compile("</" + marker + ">").matcher(text);
      while (true) {
        if (!closingTagMatcher.find(pos)) {
          LOG.assertTrue(closingTagMatcher.matches(), "Cannot find closing </" + marker + ">");
        }

        final int nextTagStart = matcher.find(pos) ? matcher.start() : text.length();
        if (closingTagMatcher.start() < nextTagStart) {
          textOffset.set(textOffset.get() + (closingTagMatcher.start() - pos));
          document.deleteString(textOffset.get(), textOffset.get() + (closingTagMatcher.end() - closingTagMatcher.start()));
          toContinueFrom = closingTagMatcher.end();
          break;
        }

        textOffset.set(textOffset.get() + (nextTagStart - pos));
        pos = extractExpectedHighlight(matcher, text, document, textOffset);
      }
    }

    final ExpectedHighlightingSet expectedHighlightingSet = highlightingTypes.get(marker);
    if (expectedHighlightingSet.enabled) {
      final HighlightInfo highlightInfo = new HighlightInfo(forcedAttributes, type, rangeStart, textOffset.get(), descr, descr,
                                                            expectedHighlightingSet.severity, expectedHighlightingSet.endOfLine, null,
                                                            false);
      expectedHighlightingSet.infos.add(highlightInfo);
    }

    return toContinueFrom;
  }

  private static final HighlightInfoType WHATEVER = new HighlightInfoType.HighlightInfoTypeImpl();

  public Collection<HighlightInfo> getExtractedHighlightInfos(){
    final Collection<HighlightInfo> result = new ArrayList<HighlightInfo>();
    final Collection<ExpectedHighlightingSet> collection = highlightingTypes.values();
    for (ExpectedHighlightingSet set : collection) {
      result.addAll(set.infos);
    }
    return result;
  }

  public void checkLineMarkers(Collection<LineMarkerInfo> markerInfos, String text) {
    String fileName = myFile == null ? "" : myFile.getName() + ": ";
    String failMessage = "";

    if (markerInfos != null) {
      for (LineMarkerInfo info : markerInfos) {
        if (!containsLineMarker(info, lineMarkerInfos.values())) {
          final int startOffset = info.startOffset;
          final int endOffset = info.endOffset;

          int y1 = StringUtil.offsetToLineNumber(text, startOffset);
          int y2 = StringUtil.offsetToLineNumber(text, endOffset);
          int x1 = startOffset - StringUtil.lineColToOffset(text, y1, 0);
          int x2 = endOffset - StringUtil.lineColToOffset(text, y2, 0);

          if (failMessage.length() != 0) failMessage += '\n';
          failMessage += fileName + "Extra line marker highlighted " +
                            "(" + (x1 + 1) + ", " + (y1 + 1) + ")" + "-" +
                            "(" + (x2 + 1) + ", " + (y2 + 1) + ")"
                            + ": '"+info.getLineMarkerTooltip()+"'"
                            ;
        }
      }
    }

    for (LineMarkerInfo expectedLineMarker : lineMarkerInfos.values()) {
      if (!containsLineMarker(expectedLineMarker, markerInfos)) {
        final int startOffset = expectedLineMarker.startOffset;
        final int endOffset = expectedLineMarker.endOffset;

        int y1 = StringUtil.offsetToLineNumber(text, startOffset);
        int y2 = StringUtil.offsetToLineNumber(text, endOffset);
        int x1 = startOffset - StringUtil.lineColToOffset(text, y1, 0);
        int x2 = endOffset - StringUtil.lineColToOffset(text, y2, 0);

        if (failMessage.length() != 0) failMessage += '\n';
        failMessage += fileName + "Line marker was not highlighted " +
                       "(" + (x1 + 1) + ", " + (y1 + 1) + ")" + "-" +
                       "(" + (x2 + 1) + ", " + (y2 + 1) + ")"
                       + ": '"+expectedLineMarker.getLineMarkerTooltip()+"'"
          ;
      }
    }

    if (failMessage.length() > 0) Assert.assertTrue(failMessage, false);
  }

  private static boolean containsLineMarker(LineMarkerInfo info, Collection<LineMarkerInfo> where) {
    final String infoTooltip = info.getLineMarkerTooltip();

    for (LineMarkerInfo markerInfo : where) {
      String markerInfoTooltip;
      if (markerInfo.startOffset == info.startOffset &&
          markerInfo.endOffset == info.endOffset &&
          ( Comparing.equal(infoTooltip, markerInfoTooltip = markerInfo.getLineMarkerTooltip())  ||
            ANY_TEXT.equals(markerInfoTooltip) ||
            ANY_TEXT.equals(infoTooltip)
          )
        ) {
        return true;
      }
    }
    return false;
  }

  public void checkResult(Collection<HighlightInfo> infos, String text) {
    String fileName = myFile == null ? "" : myFile.getName() + ": ";
    String failMessage = "";

    for (HighlightInfo info : infos) {
      if (!expectedInfosContainsInfo(info)) {
        final int startOffset = info.startOffset;
        final int endOffset = info.endOffset;
        String s = text.substring(startOffset, endOffset);
        String desc = info.description;

        int y1 = StringUtil.offsetToLineNumber(text, startOffset);
        int y2 = StringUtil.offsetToLineNumber(text, endOffset);
        int x1 = startOffset - StringUtil.lineColToOffset(text, y1, 0);
        int x2 = endOffset - StringUtil.lineColToOffset(text, y2, 0);

        if (failMessage.length() != 0) failMessage += '\n';
        failMessage += fileName + "Extra text fragment highlighted " +
                          "(" + (x1 + 1) + ", " + (y1 + 1) + ")" + "-" +
                          "(" + (x2 + 1) + ", " + (y2 + 1) + ")" +
                          " :'" +
                          s +
                          "'" + (desc == null ? "" : " (" + desc + ")")
                          + " [" + info.type + "]";
      }
    }

    final Collection<ExpectedHighlightingSet> expectedHighlights = highlightingTypes.values();
    for (ExpectedHighlightingSet highlightingSet : expectedHighlights) {
      final Set<HighlightInfo> expInfos = highlightingSet.infos;
      for (HighlightInfo expectedInfo : expInfos) {
        if (!infosContainsExpectedInfo(infos, expectedInfo) && highlightingSet.enabled) {
          final int startOffset = expectedInfo.startOffset;
          final int endOffset = expectedInfo.endOffset;
          String s = text.substring(startOffset, endOffset);
          String desc = expectedInfo.description;

          int y1 = StringUtil.offsetToLineNumber(text, startOffset);
          int y2 = StringUtil.offsetToLineNumber(text, endOffset);
          int x1 = startOffset - StringUtil.lineColToOffset(text, y1, 0);
          int x2 = endOffset - StringUtil.lineColToOffset(text, y2, 0);

          if (failMessage.length() != 0) failMessage += '\n';
          failMessage += fileName + "Text fragment was not highlighted " +
                            "(" + (x1 + 1) + ", " + (y1 + 1) + ")" + "-" +
                            "(" + (x2 + 1) + ", " + (y2 + 1) + ")" +
                            " :'" +
                            s +
                            "'" + (desc == null ? "" : " (" + desc + ")");
        }
      }
    }

    if (failMessage.length() > 0) {
      compareTexts(infos, text, failMessage);
    }
  }

  private void compareTexts(Collection<HighlightInfo> infos, String text, String failMessage) {
    final ArrayList<HighlightInfo> list = new ArrayList<HighlightInfo>(infos);
    Collections.sort(list, new Comparator<HighlightInfo>() {
      public int compare(HighlightInfo o1, HighlightInfo o2) {
        final int start = o2.startOffset - o1.startOffset;
        return start != 0 ? start : o1.endOffset - o2.endOffset;
      }
    });

    StringBuilder sb = new StringBuilder();

    int end = text.length();
    HighlightInfo prev = null;
    String prevSeverity = null;
    for (HighlightInfo info : list) {
      for (Map.Entry<String, ExpectedHighlightingSet> entry : highlightingTypes.entrySet()) {
        final ExpectedHighlightingSet set = entry.getValue();
        if (set.enabled
            && set.severity == info.getSeverity()
            && set.endOfLine == info.isAfterEndOfLine) {
          final String severity = entry.getKey();

          if (prev != null && info.endOffset > prev.startOffset) {  // nested ranges
            Assert.assertTrue("Overlapped highlightings: " + info + " and " + prev,
                              info.endOffset >= prev.endOffset);

            int offset = prevSeverity.length()*2 + 14 + (prev.description != null ? prev.description.length() : 4) + // open and closing tags
                         (prev.endOffset - prev.startOffset) + (info.endOffset - prev.endOffset);
            sb.insert(offset, "</" + severity + ">");
            sb.insert(0, text.substring(info.startOffset, prev.startOffset));
            sb.insert(0, "<" + severity + " descr=\"" + info.description + "\">");
          }
          else {  // sequential ranges
            sb.insert(0, text.substring(info.endOffset, end));
            sb.insert(0, "<" + severity + " descr=\"" + info.description + "\">" +
                         text.substring(info.startOffset, info.endOffset) +
                         "</" + severity + ">");
          }

          end = info.startOffset;
          prev = info;
          prevSeverity = severity;
          break;
        }
      }
    }
    sb.insert(0, text.substring(0, end));

    Assert.assertEquals(failMessage + "\n", myText, sb.toString());
    Assert.fail(failMessage);
  }

  private static boolean infosContainsExpectedInfo(Collection<HighlightInfo> infos, HighlightInfo expectedInfo) {
    for (HighlightInfo info : infos) {
      if (infoEquals(expectedInfo, info)) {
        return true;
      }
    }
    return false;
  }

  private boolean expectedInfosContainsInfo(HighlightInfo info) {
    if (info.getTextAttributes(null) == TextAttributes.ERASE_MARKER) return true;
    final Collection<ExpectedHighlightingSet> expectedHighlights = highlightingTypes.values();
    for (ExpectedHighlightingSet highlightingSet : expectedHighlights) {
      if (highlightingSet.severity != info.getSeverity()) continue;
      if (!highlightingSet.enabled) return true;
      final Set<HighlightInfo> infos = highlightingSet.infos;
      for (HighlightInfo expectedInfo : infos) {
        if (infoEquals(expectedInfo, info)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean infoEquals(HighlightInfo expectedInfo, HighlightInfo info) {
    if (expectedInfo == info) return true;
    return
      info.getSeverity() == expectedInfo.getSeverity() &&
      info.startOffset /*+ (info.isAfterEndOfLine ? 1 : 0)*/ == expectedInfo.startOffset &&
      info.endOffset == expectedInfo.endOffset &&
      info.isAfterEndOfLine == expectedInfo.isAfterEndOfLine &&
      (expectedInfo.type == WHATEVER || expectedInfo.type.equals(info.type)) &&
      (Comparing.strEqual(ANY_TEXT, expectedInfo.description) || Comparing.strEqual(info.description, expectedInfo.description))
      && (expectedInfo.forcedTextAttributes == null || expectedInfo.getTextAttributes(null).equals(info.getTextAttributes(null)))
      ;
  }
}
