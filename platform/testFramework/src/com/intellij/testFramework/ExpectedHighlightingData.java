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
    highlightingTypes = new THashMap<String,ExpectedHighlightingSet>();
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
  private void extractExpectedHighlightsSet(Document document) {
    String text = document.getText();

    final Set<String> markers = highlightingTypes.keySet();
    String typesRegex = "";
    for (String marker : markers) {
      typesRegex += (typesRegex.length() == 0 ? "" : "|") + "(?:" + marker + ")";
    }

    // er...
    // any code then <marker> (with optional descr="...") then any code then </marker> then any code
    @NonNls String pat = ".*?(<(" + typesRegex + ")(?: descr=\"((?:[^\"\\\\]|\\\\\"|\\\\\\\\\")*)\")?(?: type=\"([0-9A-Z_]+)\")?(?: foreground=\"([0-9xa-f]+)\")?(?: background=\"([0-9xa-f]+)\")?(?: effectcolor=\"([0-9xa-f]+)\")?(?: effecttype=\"([A-Z]+)\")?(?: fonttype=\"([0-9]+)\")?(/)?>)(.*)";
                 //"(.+?)</" + marker + ">).*";
    Pattern p = Pattern.compile(pat, Pattern.DOTALL);
    Out:
    for (; ;) {
      Matcher m = p.matcher(text);
      if (!m.matches()) break;
      int startOffset = m.start(1);
      String marker = m.group(2);
      ExpectedHighlightingSet expectedHighlightingSet = highlightingTypes.get(marker);

      while (!expectedHighlightingSet.enabled) {
        if (!m.find()) break Out;
        marker = m.group(2);
        startOffset = m.start(1);
        expectedHighlightingSet = highlightingTypes.get(marker);
      }
      int pos=3;
      @NonNls String descr = m.group(pos++);
      if (descr == null) {
        // no descr means any string by default
        descr = ANY_TEXT;
      }
      else if (descr.equals("null")) {
        // explicit "null" descr
        descr = null;
      }

      // replace: \\" to ", doesn't check symbol before sequence \\"
      if (descr != null) {
        descr = descr.replaceAll("\\\\\\\\\"", "\"");
      }

      String typeString = m.group(pos++);
      String foregroundColor = m.group(pos++);
      String backgroundColor = m.group(pos++);
      String effectColor = m.group(pos++);
      String effectType = m.group(pos++);
      String fontType = m.group(pos++);
      String closeTagMarker = m.group(pos++);
      String rest = m.group(pos++);

      String content;
      int endOffset;
      if (closeTagMarker == null) {
        Pattern pat2 = Pattern.compile("(.*?)</" + marker + ">(.*)", Pattern.DOTALL);
        final Matcher matcher2 = pat2.matcher(rest);
        LOG.assertTrue(matcher2.matches(), "Cannot find closing </" + marker + ">");
        content = matcher2.group(1);
        endOffset = m.start(pos-1) + matcher2.start(2);
      }
      else {
        // <XXX/>
        content = "";
        endOffset = m.start(pos-1);
      }

      document.replaceString(startOffset, endOffset, content);
      TextAttributes forcedAttributes = null;
      if (foregroundColor != null) {
        forcedAttributes = new TextAttributes(Color.decode(foregroundColor), Color.decode(backgroundColor),
                                                              Color.decode(effectColor), EffectType.valueOf(effectType),
                                                              Integer.parseInt(fontType));
      }

      TextRange textRange = new TextRange(startOffset, startOffset + content.length());

      HighlightInfoType type = WHATEVER;

      if (typeString != null) {
        try {
          Field field = HighlightInfoType.class.getField(typeString);
          type = (HighlightInfoType)field.get(null);
        }
        catch (Exception e) {
          // ignore
        }
        LOG.assertTrue(type != null, "Wrong highlight type: " + typeString);
      }


      HighlightInfo highlightInfo = new HighlightInfo(forcedAttributes, type, textRange.getStartOffset(), textRange.getEndOffset(), descr,
                                                       descr, expectedHighlightingSet.severity, expectedHighlightingSet.endOfLine, null,
                                                      false);
      expectedHighlightingSet.infos.add(highlightInfo);
      text = document.getText();
    }
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
        return o2.startOffset - o1.startOffset;
      }
    });

    StringBuilder sb = new StringBuilder();

    int end = text.length();
    try {
      for (HighlightInfo info : list) {
        for (Map.Entry<String, ExpectedHighlightingSet> entry : highlightingTypes.entrySet()) {
          final ExpectedHighlightingSet set = entry.getValue();
          if(set.enabled
             && set.severity == info.getSeverity()
             //&& (set.defaultErrorType.equals(info.type))
             && set.endOfLine == info.isAfterEndOfLine
            ) {
            final String severity = entry.getKey();
            sb.insert(0, text.substring(info.endOffset, end));
            sb.insert(0, "<"+
                         severity +" descr=\"" + info.description+"\">"+ text.substring(info.startOffset, info.endOffset)+"</"+
                         severity +">");
            end = info.startOffset;
            break;
          }
        }
      }
    }
    catch (IndexOutOfBoundsException e) {
      //sometimes (rarely) we have info offsets < 0 
      sb.insert(0, "<exception>" + e.getMessage() + "</exception>");
    }
    sb.insert(0, text.substring(0, end));

    Assert.assertEquals(failMessage + "\n" , myText, sb.toString());
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
