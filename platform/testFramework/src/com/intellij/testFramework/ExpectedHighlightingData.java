/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import com.intellij.util.ConstantFunction;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import junit.framework.Assert;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExpectedHighlightingData {
  private static final Logger LOG = Logger.getInstance("#com.intellij.testFramework.ExpectedHighlightingData");

  @NonNls private static final String ERROR_MARKER = "error";
  @NonNls private static final String WARNING_MARKER = "warning";
  @NonNls private static final String WEAK_WARNING_MARKER = "weak_warning";
  @NonNls private static final String INFO_MARKER = "info";
  @NonNls private static final String END_LINE_HIGHLIGHT_MARKER = "EOLError";
  @NonNls private static final String END_LINE_WARNING_MARKER = "EOLWarning";
  @NonNls private static final String LINE_MARKER = "lineMarker";

  @NotNull private final Document myDocument;
  private final PsiFile myFile;
  @NonNls private static final String ANY_TEXT = "*";
  private final String myText;

  public static class ExpectedHighlightingSet {
    private final HighlightSeverity severity;
    private final boolean endOfLine;
    private final boolean enabled;
    private final Set<HighlightInfo> infos;

    public ExpectedHighlightingSet(@NotNull HighlightSeverity severity, boolean endOfLine, boolean enabled) {
      this.severity = severity;
      this.endOfLine = endOfLine;
      this.enabled = enabled;
      infos = new THashSet<HighlightInfo>();
    }
  }
  @SuppressWarnings("WeakerAccess")
  protected final Map<String,ExpectedHighlightingSet> highlightingTypes;
  private final Map<RangeMarker, LineMarkerInfo> lineMarkerInfos = new THashMap<RangeMarker, LineMarkerInfo>();

  public void init() {
    new WriteCommandAction(null){
      @Override
      protected void run(Result result) throws Throwable {
        extractExpectedLineMarkerSet(myDocument);
        extractExpectedHighlightsSet(myDocument);
        refreshLineMarkers();
      }
    }.execute();
  }

  public ExpectedHighlightingData(@NotNull Document document,boolean checkWarnings, boolean checkInfos) {
    this(document, checkWarnings, false, checkInfos);
  }

  public ExpectedHighlightingData(@NotNull Document document,
                                  boolean checkWarnings,
                                  boolean checkWeakWarnings,
                                  boolean checkInfos) {
    this(document, checkWarnings, checkWeakWarnings, checkInfos, null);
  }

  public ExpectedHighlightingData(@NotNull final Document document, PsiFile file) {
    myDocument = document;
    myFile = file;
    myText = document.getText();
    highlightingTypes = new LinkedHashMap<String, ExpectedHighlightingSet>();
    new WriteCommandAction.Simple(file == null ? null : file.getProject()) {
      public void run() {
        boolean checkWarnings = false;
        boolean checkWeakWarnings = false;
        boolean checkInfos = false;

        highlightingTypes.put(ERROR_MARKER, new ExpectedHighlightingSet(HighlightSeverity.ERROR, false, true));
        highlightingTypes.put(WARNING_MARKER, new ExpectedHighlightingSet(HighlightSeverity.WARNING, false, checkWarnings));
        highlightingTypes.put(WEAK_WARNING_MARKER, new ExpectedHighlightingSet(HighlightSeverity.WEAK_WARNING, false, checkWeakWarnings));
        highlightingTypes.put("inject", new ExpectedHighlightingSet(HighlightInfoType.INJECTED_FRAGMENT_SEVERITY, false, checkInfos));
        highlightingTypes.put(INFO_MARKER, new ExpectedHighlightingSet(HighlightSeverity.INFORMATION, false, checkInfos));
        highlightingTypes.put("symbolName", new ExpectedHighlightingSet(HighlightInfoType.SYMBOL_TYPE_SEVERITY, false, false));
        for (SeveritiesProvider provider : Extensions.getExtensions(SeveritiesProvider.EP_NAME)) {
          for (HighlightInfoType type : provider.getSeveritiesHighlightInfoTypes()) {
            final HighlightSeverity severity = type.getSeverity(null);
            highlightingTypes.put(severity.toString(), new ExpectedHighlightingSet(severity, false, true));
          }
        }
        highlightingTypes.put(END_LINE_HIGHLIGHT_MARKER, new ExpectedHighlightingSet(HighlightSeverity.ERROR, true, true));
        highlightingTypes.put(END_LINE_WARNING_MARKER, new ExpectedHighlightingSet(HighlightSeverity.WARNING, true, checkWarnings));
        initAdditionalHighlightingTypes();
      }
    }.execute().throwException();
  }

  public ExpectedHighlightingData(@NotNull final Document document,
                                  final boolean checkWarnings,
                                  final boolean checkWeakWarnings,
                                  final boolean checkInfos,
                                  @Nullable final PsiFile file) {
    this(document, file);
    if (checkWarnings) checkWarnings();
    if (checkWeakWarnings) checkWeakWarnings();
    if (checkInfos) checkInfos();
  }

  public void checkWarnings() {
    highlightingTypes.put(WARNING_MARKER, new ExpectedHighlightingSet(HighlightSeverity.WARNING, false, true));
    highlightingTypes.put(END_LINE_WARNING_MARKER, new ExpectedHighlightingSet(HighlightSeverity.WARNING, true, true));

  }
  public void checkWeakWarnings() {
    highlightingTypes.put(WEAK_WARNING_MARKER, new ExpectedHighlightingSet(HighlightSeverity.WEAK_WARNING, false, true));
  }
  public void checkInfos() {
    highlightingTypes.put(INFO_MARKER, new ExpectedHighlightingSet(HighlightSeverity.INFORMATION, false, true));
    highlightingTypes.put("inject", new ExpectedHighlightingSet(HighlightInfoType.INJECTED_FRAGMENT_SEVERITY, false, true));
  }
  public void checkSymbolNames() {
    highlightingTypes.put("symbolName", new ExpectedHighlightingSet(HighlightInfoType.SYMBOL_TYPE_SEVERITY, false, true));
  }

  private void refreshLineMarkers() {
    for (Map.Entry<RangeMarker, LineMarkerInfo> entry : lineMarkerInfos.entrySet()) {
      RangeMarker rangeMarker = entry.getKey();
      int startOffset = rangeMarker.getStartOffset();
      int endOffset = rangeMarker.getEndOffset();
      final LineMarkerInfo value = entry.getValue();
      LineMarkerInfo markerInfo = new LineMarkerInfo<PsiElement>(value.getElement(), new TextRange(startOffset,endOffset), null, value.updatePass, new Function<PsiElement,String>() {
        @Override
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

    while (true) {
      Matcher m = p.matcher(text);
      if (!m.matches()) break;
      int startOffset = m.start(1);
      final String descr = m.group(3) != null ? m.group(3) : ANY_TEXT;
      String rest = m.group(4);

      document.replaceString(startOffset, m.end(1), "");

      final Matcher matcher2 = pat2.matcher(rest);
      LOG.assertTrue(matcher2.matches(), "Cannot find closing </" + LINE_MARKER + ">");
      String content = matcher2.group(1);
      int endOffset = startOffset + matcher2.start(3);
      String endTag = matcher2.group(2);

      document.replaceString(startOffset, endOffset, content);
      endOffset -= endTag.length();

      LineMarkerInfo markerInfo = new LineMarkerInfo<PsiElement>(myFile, new TextRange(startOffset, endOffset), null, Pass.LINE_MARKERS,
                                                                 new ConstantFunction<PsiElement, String>(descr), null,
                                                                 GutterIconRenderer.Alignment.RIGHT);

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
                                "(?:\\s+descr=\"((?:[^\"]|\\\\\"|\\\\\\\\\"|\\\\\\[|\\\\\\])*)\")?" +
                                "(?:\\s+type=\"([0-9A-Z_]+)\")?" +
                                "(?:\\s+foreground=\"([0-9xa-f]+)\")?" +
                                "(?:\\s+background=\"([0-9xa-f]+)\")?" +
                                "(?:\\s+effectcolor=\"([0-9xa-f]+)\")?" +
                                "(?:\\s+effecttype=\"([A-Z]+)\")?" +
                                "(?:\\s+fonttype=\"([0-9]+)\")?" +
                                "(?:\\s+textAttributesKey=\"((?:[^\"]|\\\\\"|\\\\\\\\\"|\\\\\\[|\\\\\\])*)\")?" +
                                "(/)?>";

    final Matcher matcher = Pattern.compile(openingTagRx).matcher(text);
    int pos = 0;
    final Ref<Integer> textOffset = Ref.create(0);
    while (matcher.find(pos)) {
      textOffset.set(textOffset.get() + matcher.start() - pos);
      pos = extractExpectedHighlight(matcher, text, document, textOffset);
    }
  }

  private int extractExpectedHighlight(final Matcher matcher, final String text, final Document document, final Ref<Integer> textOffset) {
    document.deleteString(textOffset.get(), textOffset.get() + matcher.end() - matcher.start());

    int groupIdx = 1;
    final String marker = matcher.group(groupIdx++);
    @NonNls String descr = matcher.group(groupIdx++);
    final String typeString = matcher.group(groupIdx++);
    final String foregroundColor = matcher.group(groupIdx++);
    final String backgroundColor = matcher.group(groupIdx++);
    final String effectColor = matcher.group(groupIdx++);
    final String effectType = matcher.group(groupIdx++);
    final String fontType = matcher.group(groupIdx++);
    final String attrKey = matcher.group(groupIdx++);
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
      catch (Exception e) {
        LOG.error(e);
      }
      LOG.assertTrue(type != null, "Wrong highlight type: " + typeString);
    }

    TextAttributes forcedAttributes = null;
    if (foregroundColor != null) {
      //noinspection MagicConstant
      forcedAttributes = new TextAttributes(Color.decode(foregroundColor), Color.decode(backgroundColor), Color.decode(effectColor),
                                            EffectType.valueOf(effectType), Integer.parseInt(fontType));
    }

    final int rangeStart = textOffset.get();
    final int toContinueFrom;
    if (closed) {
      toContinueFrom = matcher.end();
    }
    else {
      int pos = matcher.end();
      final Matcher closingTagMatcher = Pattern.compile("</" + marker + ">").matcher(text);
      while (true) {
        if (!closingTagMatcher.find(pos)) {
          LOG.error("Cannot find closing </" + marker + "> in position " + pos);
        }

        final int nextTagStart = matcher.find(pos) ? matcher.start() : text.length();
        if (closingTagMatcher.start() < nextTagStart) {
          textOffset.set(textOffset.get() + closingTagMatcher.start() - pos);
          document.deleteString(textOffset.get(), textOffset.get() + closingTagMatcher.end() - closingTagMatcher.start());
          toContinueFrom = closingTagMatcher.end();
          break;
        }

        textOffset.set(textOffset.get() + nextTagStart - pos);
        pos = extractExpectedHighlight(matcher, text, document, textOffset);
      }
    }

    final ExpectedHighlightingSet expectedHighlightingSet = highlightingTypes.get(marker);
    if (expectedHighlightingSet.enabled) {
      TextAttributesKey forcedTextAttributesKey = attrKey == null ? null : TextAttributesKey.createTextAttributesKey(attrKey);
      HighlightInfo.Builder builder =
        HighlightInfo.newHighlightInfo(type).range(rangeStart, textOffset.get()).severity(expectedHighlightingSet.severity);

      if (forcedAttributes != null) builder.textAttributes(forcedAttributes);
      if (forcedTextAttributesKey != null) builder.textAttributes(forcedTextAttributesKey);
      if (descr != null) { builder.description(descr); builder.unescapedToolTip(descr); }
      if (expectedHighlightingSet.endOfLine) builder.endOfLine();
      HighlightInfo highlightInfo = builder.createUnconditionally();
      expectedHighlightingSet.infos.add(highlightInfo);
    }

    return toContinueFrom;
  }

  private static final HighlightInfoType WHATEVER = new HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION,
                                                                                                HighlighterColors.TEXT);

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

          if (!failMessage.isEmpty()) failMessage += '\n';
          failMessage += fileName + "Extra line marker highlighted " +
                            "(" + (x1 + 1) + ", " + (y1 + 1) + ")" + "-" +
                            "(" + (x2 + 1) + ", " + (y2 + 1) + ")"
                            + ": '"+info.getLineMarkerTooltip()+"'"
                            ;
        }
      }
    }

    for (LineMarkerInfo expectedLineMarker : lineMarkerInfos.values()) {
      if (markerInfos != null && !containsLineMarker(expectedLineMarker, markerInfos)) {
        final int startOffset = expectedLineMarker.startOffset;
        final int endOffset = expectedLineMarker.endOffset;

        int y1 = StringUtil.offsetToLineNumber(text, startOffset);
        int y2 = StringUtil.offsetToLineNumber(text, endOffset);
        int x1 = startOffset - StringUtil.lineColToOffset(text, y1, 0);
        int x2 = endOffset - StringUtil.lineColToOffset(text, y2, 0);

        if (!failMessage.isEmpty()) failMessage += '\n';
        failMessage += fileName + "Line marker was not highlighted " +
                       "(" + (x1 + 1) + ", " + (y1 + 1) + ")" + "-" +
                       "(" + (x2 + 1) + ", " + (y2 + 1) + ")"
                       + ": '"+expectedLineMarker.getLineMarkerTooltip()+"'"
          ;
      }
    }

    if (!failMessage.isEmpty()) Assert.fail(failMessage);
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
    checkResult(infos, text, null);
  }

  public void checkResult(Collection<HighlightInfo> infos, String text, @Nullable String filePath) {
    if (filePath == null) {
      VirtualFile virtualFile = myFile == null? null : myFile.getVirtualFile();
      filePath = virtualFile == null? null : virtualFile.getUserData(VfsTestUtil.TEST_DATA_FILE_PATH);
    }
    String fileName = myFile == null ? "" : myFile.getName() + ": ";
    String failMessage = "";

    for (HighlightInfo info : reverseCollection(infos)) {
      if (!expectedInfosContainsInfo(info)) {
        final int startOffset = info.startOffset;
        final int endOffset = info.endOffset;
        String s = text.substring(startOffset, endOffset);
        String desc = info.getDescription();

        int y1 = StringUtil.offsetToLineNumber(text, startOffset);
        int y2 = StringUtil.offsetToLineNumber(text, endOffset);
        int x1 = startOffset - StringUtil.lineColToOffset(text, y1, 0);
        int x2 = endOffset - StringUtil.lineColToOffset(text, y2, 0);

        if (!failMessage.isEmpty()) failMessage += '\n';
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
    for (ExpectedHighlightingSet highlightingSet : reverseCollection(expectedHighlights)) {
      final Set<HighlightInfo> expInfos = highlightingSet.infos;
      for (HighlightInfo expectedInfo : expInfos) {
        if (!infosContainsExpectedInfo(infos, expectedInfo) && highlightingSet.enabled) {
          final int startOffset = expectedInfo.startOffset;
          final int endOffset = expectedInfo.endOffset;
          String s = text.substring(startOffset, endOffset);
          String desc = expectedInfo.getDescription();

          int y1 = StringUtil.offsetToLineNumber(text, startOffset);
          int y2 = StringUtil.offsetToLineNumber(text, endOffset);
          int x1 = startOffset - StringUtil.lineColToOffset(text, y1, 0);
          int x2 = endOffset - StringUtil.lineColToOffset(text, y2, 0);

          if (!failMessage.isEmpty()) failMessage += '\n';
          failMessage += fileName + "Text fragment was not highlighted " +
                            "(" + (x1 + 1) + ", " + (y1 + 1) + ")" + "-" +
                            "(" + (x2 + 1) + ", " + (y2 + 1) + ")" +
                            " :'" +
                            s +
                            "'" + (desc == null ? "" : " (" + desc + ")");
        }
      }
    }

    if (!failMessage.isEmpty()) {
      compareTexts(infos, text, failMessage + "\n", filePath);
    }
  }

  private static <T> List<T> reverseCollection(Collection<T> infos) {
    return ContainerUtil.reverse(infos instanceof List ? (List<T>)infos : new ArrayList<T>(infos));
  }

  private void compareTexts(Collection<HighlightInfo> infos, String text, String failMessage, @Nullable String filePath) {
    String actual = composeText(highlightingTypes,  infos, text);
    if (filePath != null && !myText.equals(actual)) {
      // uncomment to overwrite, don't forget to revert on commit!
      //VfsTestUtil.overwriteTestData(filePath, actual);
      //return;
      throw new FileComparisonFailure(failMessage, myText, actual, filePath);
    }
    Assert.assertEquals(failMessage + "\n", myText, actual);
    Assert.fail(failMessage);
  }

  public static String composeText(final Map<String, ExpectedHighlightingSet> types, Collection<HighlightInfo> infos, String text) {
    // filter highlighting data and map each highlighting to a tag name
    List<Pair<String, HighlightInfo>> list = ContainerUtil.mapNotNull(infos, new NullableFunction<HighlightInfo, Pair<String,HighlightInfo>>() {
      @Override
      public Pair<String, HighlightInfo> fun(HighlightInfo info) {
        for (Map.Entry<String, ExpectedHighlightingSet> entry : types.entrySet()) {
          final ExpectedHighlightingSet set = entry.getValue();
          if (set.enabled && set.severity == info.getSeverity() && set.endOfLine == info.isAfterEndOfLine()) {
            return Pair.create(entry.getKey(), info);
          }
        }
        return null;
      }
    });

    // sort filtered highlighting data by end offset in descending order
    Collections.sort(list, new Comparator<Pair<String, HighlightInfo>>() {
      @Override
      public int compare(Pair<String, HighlightInfo> o1, Pair<String, HighlightInfo> o2) {
        HighlightInfo i1 = o1.second;
        HighlightInfo i2 = o2.second;

        int byEnds = i2.endOffset - i1.endOffset;
        if (byEnds != 0) return byEnds;

        if (!i1.isAfterEndOfLine() && !i2.isAfterEndOfLine()) {
          int byStarts = i1.startOffset - i2.startOffset;
          if (byStarts != 0) return byStarts;
        }
        else {
          int byEOL = Comparing.compare(i2.isAfterEndOfLine(), i1.isAfterEndOfLine());
          if (byEOL != 0) return byEOL;
        }

        int bySeverity = i2.getSeverity().compareTo(i1.getSeverity());
        if (bySeverity != 0) return bySeverity;

        return Comparing.compare(i1.getDescription(), i2.getDescription());
      }
    });

    // combine highlighting data with original text
    StringBuilder sb = new StringBuilder();
    Pair<Integer, Integer> result = composeText(sb, list, 0, text, text.length(), 0);
    sb.insert(0, text.substring(0, result.second));
    return sb.toString();
  }

  private static Pair<Integer, Integer> composeText(StringBuilder sb,
                                                    List<Pair<String, HighlightInfo>> list, int index,
                                                    String text, int endPos, int startPos) {
    int i = index;
    while (i < list.size()) {
      Pair<String, HighlightInfo> pair = list.get(i);
      HighlightInfo info = pair.second;
      if (info.endOffset <= startPos) {
        break;
      }

      String severity = pair.first;
      HighlightInfo prev = i < list.size() - 1 ? list.get(i + 1).second : null;

      sb.insert(0, text.substring(info.endOffset, endPos));
      sb.insert(0, "</" + severity + ">");
      endPos = info.endOffset;
      if (prev != null && prev.endOffset > info.startOffset) {
        Pair<Integer, Integer> result = composeText(sb, list, i + 1, text, endPos, info.startOffset);
        i = result.first - 1;
        endPos = result.second;
      }
      sb.insert(0, text.substring(info.startOffset, endPos));
      sb.insert(0, "<" + severity + " descr=\"" + info.getDescription() + "\">");

      endPos = info.startOffset;
      i++;
    }

    return Pair.create(i, endPos);
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
    if (info.getTextAttributes(null, null) == TextAttributes.ERASE_MARKER) return true;
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
      info.startOffset == expectedInfo.startOffset &&
      info.endOffset == expectedInfo.endOffset &&
      info.isAfterEndOfLine() == expectedInfo.isAfterEndOfLine() &&
      (expectedInfo.type == WHATEVER || expectedInfo.type.equals(info.type)) &&
      (Comparing.strEqual(ANY_TEXT, expectedInfo.getDescription()) || Comparing.strEqual(info.getDescription(),
                                                                                         expectedInfo.getDescription())) &&
      (expectedInfo.forcedTextAttributes == null || Comparing.equal(expectedInfo.getTextAttributes(null, null),
                                                                    info.getTextAttributes(null, null))) &&
      (expectedInfo.forcedTextAttributesKey == null || expectedInfo.forcedTextAttributesKey.equals(info.forcedTextAttributesKey));
  }
}
