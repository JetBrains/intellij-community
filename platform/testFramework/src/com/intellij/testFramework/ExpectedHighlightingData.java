/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.testFramework;

import com.intellij.CommonBundle;
import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeveritiesProvider;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.intellij.openapi.util.Pair.pair;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author cdr
 */
public class ExpectedHighlightingData {
  private static final String ERROR_MARKER = CodeInsightTestFixture.ERROR_MARKER;
  private static final String WARNING_MARKER = CodeInsightTestFixture.WARNING_MARKER;
  private static final String WEAK_WARNING_MARKER = CodeInsightTestFixture.WEAK_WARNING_MARKER;
  private static final String INFO_MARKER = CodeInsightTestFixture.INFO_MARKER;
  private static final String END_LINE_HIGHLIGHT_MARKER = CodeInsightTestFixture.END_LINE_HIGHLIGHT_MARKER;
  private static final String END_LINE_WARNING_MARKER = CodeInsightTestFixture.END_LINE_WARNING_MARKER;
  private static final String INJECT_MARKER = "inject";
  private static final String SYMBOL_NAME_MARKER = "symbolName";
  private static final String LINE_MARKER = "lineMarker";
  private static final String ANY_TEXT = "*";

  private static final HighlightInfoType WHATEVER =
    new HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, HighlighterColors.TEXT);

  public static class ExpectedHighlightingSet {
    private final HighlightSeverity severity;
    private final boolean endOfLine;
    private final boolean enabled;
    private final Set<HighlightInfo> infos;

    public ExpectedHighlightingSet(@NotNull HighlightSeverity severity, boolean endOfLine, boolean enabled) {
      this.severity = severity;
      this.endOfLine = endOfLine;
      this.enabled = enabled;
      this.infos = new THashSet<>();
    }
  }

  private final Map<String, ExpectedHighlightingSet> myHighlightingTypes = new LinkedHashMap<>();
  private final Map<RangeMarker, LineMarkerInfo> myLineMarkerInfos = new THashMap<>();
  private final Document myDocument;
  @SuppressWarnings("StatefulEp") private final PsiFile myFile;
  private final String myText;
  private boolean myIgnoreExtraHighlighting;

  public ExpectedHighlightingData(@NotNull Document document, boolean checkWarnings, boolean checkInfos) {
    this(document, checkWarnings, false, checkInfos);
  }

  public ExpectedHighlightingData(@NotNull Document document, boolean checkWarnings, boolean checkWeakWarnings, boolean checkInfos) {
    this(document, checkWarnings, checkWeakWarnings, checkInfos, null);
  }

  public ExpectedHighlightingData(@NotNull Document document,
                                  boolean checkWarnings,
                                  boolean checkWeakWarnings,
                                  boolean checkInfos,
                                  @Nullable PsiFile file) {
    this(document, checkWarnings, checkWeakWarnings, checkInfos, false, file);
  }

  public ExpectedHighlightingData(@NotNull Document document,
                                  boolean checkWarnings,
                                  boolean checkWeakWarnings,
                                  boolean checkInfos,
                                  boolean ignoreExtraHighlighting,
                                  @Nullable PsiFile file) {
    this(document, file);
    myIgnoreExtraHighlighting = ignoreExtraHighlighting;
    if (checkWarnings) checkWarnings();
    if (checkWeakWarnings) checkWeakWarnings();
    if (checkInfos) checkInfos();
  }

  public ExpectedHighlightingData(@NotNull Document document, @Nullable PsiFile file) {
    myDocument = document;
    myFile = file;
    myText = document.getText();

    registerHighlightingType(ERROR_MARKER, new ExpectedHighlightingSet(HighlightSeverity.ERROR, false, true));
    registerHighlightingType(WARNING_MARKER, new ExpectedHighlightingSet(HighlightSeverity.WARNING, false, false));
    registerHighlightingType(WEAK_WARNING_MARKER, new ExpectedHighlightingSet(HighlightSeverity.WEAK_WARNING, false, false));
    registerHighlightingType(INJECT_MARKER, new ExpectedHighlightingSet(HighlightInfoType.INJECTED_FRAGMENT_SEVERITY, false, false));
    registerHighlightingType(INFO_MARKER, new ExpectedHighlightingSet(HighlightSeverity.INFORMATION, false, false));
    registerHighlightingType(SYMBOL_NAME_MARKER, new ExpectedHighlightingSet(HighlightInfoType.SYMBOL_TYPE_SEVERITY, false, false));
    for (SeveritiesProvider provider : Extensions.getExtensions(SeveritiesProvider.EP_NAME)) {
      for (HighlightInfoType type : provider.getSeveritiesHighlightInfoTypes()) {
        HighlightSeverity severity = type.getSeverity(null);
        registerHighlightingType(severity.getName(), new ExpectedHighlightingSet(severity, false, true));
      }
    }
    registerHighlightingType(END_LINE_HIGHLIGHT_MARKER, new ExpectedHighlightingSet(HighlightSeverity.ERROR, true, true));
    registerHighlightingType(END_LINE_WARNING_MARKER, new ExpectedHighlightingSet(HighlightSeverity.WARNING, true, false));
  }

  public boolean hasLineMarkers() {
    return !myLineMarkerInfos.isEmpty();
  }

  public void init() {
    WriteCommandAction.writeCommandAction(null).run(() -> {
      extractExpectedLineMarkerSet(myDocument);
      extractExpectedHighlightsSet(myDocument);
      refreshLineMarkers();
    });
  }

  public void checkWarnings() {
    registerHighlightingType(WARNING_MARKER, new ExpectedHighlightingSet(HighlightSeverity.WARNING, false, true));
    registerHighlightingType(END_LINE_WARNING_MARKER, new ExpectedHighlightingSet(HighlightSeverity.WARNING, true, true));
  }

  public void checkWeakWarnings() {
    registerHighlightingType(WEAK_WARNING_MARKER, new ExpectedHighlightingSet(HighlightSeverity.WEAK_WARNING, false, true));
  }

  public void checkInfos() {
    registerHighlightingType(INFO_MARKER, new ExpectedHighlightingSet(HighlightSeverity.INFORMATION, false, true));
    registerHighlightingType(INJECT_MARKER, new ExpectedHighlightingSet(HighlightInfoType.INJECTED_FRAGMENT_SEVERITY, false, true));
  }

  public void checkSymbolNames() {
    registerHighlightingType(SYMBOL_NAME_MARKER, new ExpectedHighlightingSet(HighlightInfoType.SYMBOL_TYPE_SEVERITY, false, true));
  }

  public void registerHighlightingType(@NotNull String key, @NotNull ExpectedHighlightingSet highlightingSet) {
    myHighlightingTypes.put(key, highlightingSet);
  }

  private void refreshLineMarkers() {
    for (Map.Entry<RangeMarker, LineMarkerInfo> entry : myLineMarkerInfos.entrySet()) {
      RangeMarker rangeMarker = entry.getKey();
      int startOffset = rangeMarker.getStartOffset();
      int endOffset = rangeMarker.getEndOffset();
      LineMarkerInfo value = entry.getValue();
      PsiElement element = value.getElement();
      assert element != null : value;
      TextRange range = new TextRange(startOffset, endOffset);
      String tooltip = value.getLineMarkerTooltip();
      MyLineMarkerInfo markerInfo =
        new MyLineMarkerInfo(element, range, value.updatePass, GutterIconRenderer.Alignment.RIGHT, tooltip);
      entry.setValue(markerInfo);
    }
  }

  private void extractExpectedLineMarkerSet(Document document) {
    String text = document.getText();

    String pat = ".*?((<" + LINE_MARKER + ")(?: descr=\"((?:[^\"\\\\]|\\\\\")*)\")?>)(.*)";
    Pattern openingTagRx = Pattern.compile(pat, Pattern.DOTALL);
    Pattern closingTagRx = Pattern.compile("(.*?)(</" + LINE_MARKER + ">)(.*)", Pattern.DOTALL);

    while (true) {
      Matcher opening = openingTagRx.matcher(text);
      if (!opening.matches()) break;

      int startOffset = opening.start(1);
      String descr = opening.group(3) != null ? opening.group(3) : ANY_TEXT;
      String rest = opening.group(4);

      Matcher closing = closingTagRx.matcher(rest);
      if (!closing.matches()) {
        fail("Cannot find closing </" + LINE_MARKER + ">");
      }

      document.replaceString(startOffset, opening.end(1), "");

      String content = closing.group(1);
      int endOffset = startOffset + closing.start(3);
      String endTag = closing.group(2);

      document.replaceString(startOffset, endOffset, content);
      endOffset -= endTag.length();

      PsiElement leaf = Objects.requireNonNull(myFile.findElementAt(startOffset));
      TextRange range = new TextRange(startOffset, endOffset);
      String tooltip = StringUtil.unescapeStringCharacters(descr);
      LineMarkerInfo<PsiElement> markerInfo =
        new MyLineMarkerInfo(leaf, range, Pass.LINE_MARKERS, GutterIconRenderer.Alignment.RIGHT, tooltip);
      myLineMarkerInfos.put(document.createRangeMarker(startOffset, endOffset), markerInfo);

      text = document.getText();
    }
  }

  /**
   * Removes highlights (bounded with <marker>...</marker>) from test case file.
   */
  private void extractExpectedHighlightsSet(Document document) {
    String text = document.getText();

    Set<String> markers = myHighlightingTypes.keySet();
    String typesRx = "(?:" + StringUtil.join(markers, ")|(?:") + ")";
    String openingTagRx = "<(" + typesRx + ")" +
                                "(?:\\s+descr=\"((?:[^\"]|\\\\\"|\\\\\\\\\"|\\\\\\[|\\\\])*)\")?" +
                                "(?:\\s+type=\"([0-9A-Z_]+)\")?" +
                                "(?:\\s+foreground=\"([0-9xa-f]+)\")?" +
                                "(?:\\s+background=\"([0-9xa-f]+)\")?" +
                                "(?:\\s+effectcolor=\"([0-9xa-f]+)\")?" +
                                "(?:\\s+effecttype=\"([A-Z]+)\")?" +
                                "(?:\\s+fonttype=\"([0-9]+)\")?" +
                                "(?:\\s+textAttributesKey=\"((?:[^\"]|\\\\\"|\\\\\\\\\"|\\\\\\[|\\\\])*)\")?" +
                                "(?:\\s+bundleMsg=\"((?:[^\"]|\\\\\"|\\\\\\\\\")*)\")?" +
                                "(/)?>";

    Matcher matcher = Pattern.compile(openingTagRx).matcher(text);
    int pos = 0;
    Ref<Integer> textOffset = Ref.create(0);
    while (matcher.find(pos)) {
      textOffset.set(textOffset.get() + matcher.start() - pos);
      pos = extractExpectedHighlight(matcher, text, document, textOffset);
    }
  }

  private int extractExpectedHighlight(Matcher matcher, String text, Document document, Ref<Integer> textOffset) {
    document.deleteString(textOffset.get(), textOffset.get() + matcher.end() - matcher.start());

    int groupIdx = 1;
    String marker = matcher.group(groupIdx++);
    String descr = matcher.group(groupIdx++);
    String typeString = matcher.group(groupIdx++);
    String foregroundColor = matcher.group(groupIdx++);
    String backgroundColor = matcher.group(groupIdx++);
    String effectColor = matcher.group(groupIdx++);
    String effectType = matcher.group(groupIdx++);
    String fontType = matcher.group(groupIdx++);
    String attrKey = matcher.group(groupIdx++);
    String bundleMessage = matcher.group(groupIdx++);
    boolean closed = matcher.group(groupIdx) != null;

    if (descr == null) {
      descr = ANY_TEXT;  // no descr means any string by default
    }
    else if (descr.equals("null")) {
      descr = null;  // explicit "null" descr
    }
    if (descr != null) {
      descr = descr.replaceAll("\\\\\\\\\"", "\"");  // replace: \\" to ", doesn't check symbol before sequence \\"
      descr = descr.replaceAll("\\\\\"", "\"");
    }

    HighlightInfoType type = WHATEVER;
    if (typeString != null) {
      try {
        type = getTypeByName(typeString);
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
      if (type == null) {
        fail("Wrong highlight type: " + typeString);
      }
    }

    TextAttributes forcedAttributes = null;
    if (foregroundColor != null) {
      @JdkConstants.FontStyle int ft = Integer.parseInt(fontType);
      forcedAttributes = new TextAttributes(
        Color.decode(foregroundColor), Color.decode(backgroundColor), Color.decode(effectColor), EffectType.valueOf(effectType), ft);
    }

    int rangeStart = textOffset.get();
    int toContinueFrom;
    if (closed) {
      toContinueFrom = matcher.end();
    }
    else {
      int pos = matcher.end();
      Matcher closingTagMatcher = Pattern.compile("</" + marker + ">").matcher(text);
      while (true) {
        if (!closingTagMatcher.find(pos)) {
          toContinueFrom = pos;
          break;
        }

        int nextTagStart = matcher.find(pos) ? matcher.start() : text.length();
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

    ExpectedHighlightingSet expectedHighlightingSet = myHighlightingTypes.get(marker);
    if (expectedHighlightingSet.enabled) {
      TextAttributesKey forcedTextAttributesKey = attrKey == null ? null : TextAttributesKey.createTextAttributesKey(attrKey);
      HighlightInfo.Builder builder =
        HighlightInfo.newHighlightInfo(type).range(rangeStart, textOffset.get()).severity(expectedHighlightingSet.severity);

      if (forcedAttributes != null) builder.textAttributes(forcedAttributes);
      if (forcedTextAttributesKey != null) builder.textAttributes(forcedTextAttributesKey);
      if (bundleMessage != null) {
        List<String> split = StringUtil.split(bundleMessage, "|");
        ResourceBundle bundle = ResourceBundle.getBundle(split.get(0));
        descr = CommonBundle.message(bundle, split.get(1), split.stream().skip(2).toArray());
      }
      if (descr != null) {
        builder.description(descr);
        builder.unescapedToolTip(descr);
      }
      if (expectedHighlightingSet.endOfLine) builder.endOfLine();
      HighlightInfo highlightInfo = builder.createUnconditionally();
      expectedHighlightingSet.infos.add(highlightInfo);
    }

    return toContinueFrom;
  }

  protected HighlightInfoType getTypeByName(String typeString) throws Exception {
    Field field = HighlightInfoType.class.getField(typeString);
    return (HighlightInfoType)field.get(null);
  }

  public void checkLineMarkers(@NotNull Collection<LineMarkerInfo> markerInfos, @NotNull String text) {
    String fileName = myFile == null ? "" : myFile.getName() + ": ";
    StringBuilder failMessage = new StringBuilder();

    for (LineMarkerInfo info : markerInfos) {
      if (!containsLineMarker(info, myLineMarkerInfos.values())) {
        if (failMessage.length() > 0) failMessage.append('\n');
        failMessage.append(fileName).append("extra ")
          .append(rangeString(text, info.startOffset, info.endOffset))
          .append(": '").append(info.getLineMarkerTooltip()).append('\'');
      }
    }

    for (LineMarkerInfo expectedLineMarker : myLineMarkerInfos.values()) {
      if (markerInfos.isEmpty() || !containsLineMarker(expectedLineMarker, markerInfos)) {
        if (failMessage.length() > 0) failMessage.append('\n');
        failMessage.append(fileName).append("missing ")
          .append(rangeString(text, expectedLineMarker.startOffset, expectedLineMarker.endOffset))
          .append(": '").append(expectedLineMarker.getLineMarkerTooltip()).append('\'');
      }
    }

    if (failMessage.length() > 0) {
      fail(failMessage.toString());
    }
  }

  private static boolean containsLineMarker(LineMarkerInfo info, Collection<LineMarkerInfo> where) {
    String infoTooltip = info.getLineMarkerTooltip();
    for (LineMarkerInfo markerInfo : where) {
      String markerInfoTooltip;
      if (markerInfo.startOffset == info.startOffset &&
          markerInfo.endOffset == info.endOffset &&
          (Comparing.equal(infoTooltip, markerInfoTooltip = markerInfo.getLineMarkerTooltip()) ||
           ANY_TEXT.equals(markerInfoTooltip) ||
           ANY_TEXT.equals(infoTooltip))) {
        return true;
      }
    }

    return false;
  }

  public void checkResult(Collection<HighlightInfo> infos, String text) {
    checkResult(infos, text, null);
  }

  public void checkResult(Collection<HighlightInfo> infos, String text, @Nullable String filePath) {
    String fileName = myFile == null ? "" : myFile.getName() + ": ";
    StringBuilder failMessage = new StringBuilder();

    for (HighlightInfo info : reverseCollection(infos)) {
      if (!expectedInfosContainsInfo(info) && !myIgnoreExtraHighlighting) {
        int startOffset = info.startOffset;
        int endOffset = info.endOffset;
        String s = text.substring(startOffset, endOffset);
        String desc = info.getDescription();

        if (failMessage.length() > 0) {
          failMessage.append('\n');
        }
        failMessage.append(fileName).append("extra ")
          .append(rangeString(text, startOffset, endOffset))
          .append(": '").append(s).append('\'');
        if (desc != null) {
          failMessage.append(" (").append(desc).append(')');
        }
        failMessage.append(" [").append(info.type).append(']');
      }
    }

    Collection<ExpectedHighlightingSet> expectedHighlights = myHighlightingTypes.values();
    for (ExpectedHighlightingSet highlightingSet : reverseCollection(expectedHighlights)) {
      Set<HighlightInfo> expInfos = highlightingSet.infos;
      for (HighlightInfo expectedInfo : expInfos) {
        if (!infosContainsExpectedInfo(infos, expectedInfo) && highlightingSet.enabled) {
          int startOffset = expectedInfo.startOffset;
          int endOffset = expectedInfo.endOffset;
          String s = text.substring(startOffset, endOffset);
          String desc = expectedInfo.getDescription();

          if (failMessage.length() > 0) {
            failMessage.append('\n');
          }
          failMessage.append(fileName).append("missing ")
            .append(rangeString(text, startOffset, endOffset))
            .append(": '").append(s).append('\'');
          if (desc != null) {
            failMessage.append(" (").append(desc).append(")");
          }
        }
      }
    }

    if (failMessage.length() > 0) {
      if (filePath == null && myFile != null) {
        VirtualFile file = myFile.getVirtualFile();
        if (file != null) {
          filePath = file.getUserData(VfsTestUtil.TEST_DATA_FILE_PATH);
        }
      }

      failMessage.append('\n');
      compareTexts(infos, text, failMessage.toString(), filePath);
    }
  }

  private static <T> List<T> reverseCollection(Collection<T> infos) {
    return ContainerUtil.reverse(infos instanceof List ? (List<T>)infos : new ArrayList<>(infos));
  }

  private void compareTexts(Collection<HighlightInfo> infos, String text, String failMessage, @Nullable String filePath) {
    String actual = composeText(myHighlightingTypes, infos, text);
    if (filePath != null && !myText.equals(actual)) {
      // uncomment to overwrite, don't forget to revert on commit!
      //VfsTestUtil.overwriteTestData(filePath, actual);
      //return;
      throw new FileComparisonFailure(failMessage, myText, actual, filePath);
    }
    assertEquals(failMessage + "\n", myText, actual);
    fail(failMessage);
  }

  private static String findTag(Map<String, ExpectedHighlightingSet> types, HighlightInfo info) {
    Map.Entry<String, ExpectedHighlightingSet> entry = ContainerUtil.find(
      types.entrySet(),
      e -> e.getValue().enabled && e.getValue().severity == info.getSeverity() && e.getValue().endOfLine == info.isAfterEndOfLine());
    return entry != null ? entry.getKey() : null;
  }

  public static String composeText(Map<String, ExpectedHighlightingSet> types, Collection<HighlightInfo> infos, String text) {
    // filter highlighting data and map each highlighting to a tag name
    List<Pair<String, HighlightInfo>> list = infos.stream()
      .map(info -> pair(findTag(types, info), info))
      .filter(p -> p.first != null)
      .collect(Collectors.toList());
    boolean showAttributesKeys =
      types.values().stream().flatMap(set -> set.infos.stream()).anyMatch(i -> i.forcedTextAttributesKey != null);

    // sort filtered highlighting data by end offset in descending order
    Collections.sort(list, (o1, o2) -> {
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
    });

    // combine highlighting data with original text
    StringBuilder sb = new StringBuilder();
    int[] offsets = composeText(sb, list, 0, text, text.length(), -1, showAttributesKeys);
    sb.insert(0, text.substring(0, offsets[1]));
    return sb.toString();
  }

  private static int[] composeText(StringBuilder sb,
                                   List<Pair<String, HighlightInfo>> list, int index,
                                   String text, int endPos, int startPos,
                                   boolean showAttributesKeys) {
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
      sb.insert(0, "</" + severity + '>');
      endPos = info.endOffset;
      if (prev != null && prev.endOffset > info.startOffset) {
        int[] offsets = composeText(sb, list, i + 1, text, endPos, info.startOffset, showAttributesKeys);
        i = offsets[0] - 1;
        endPos = offsets[1];
      }
      sb.insert(0, text.substring(info.startOffset, endPos));

      String str = '<' + severity + " descr=\"" + StringUtil.escapeQuotes(String.valueOf(info.getDescription())) + '"';
      if (showAttributesKeys) {
        str += " textAttributesKey=\"" + info.forcedTextAttributesKey + '"';
      }
      str += '>';
      sb.insert(0, str);

      endPos = info.startOffset;
      i++;
    }

    return new int[]{i, endPos};
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
    Collection<ExpectedHighlightingSet> expectedHighlights = myHighlightingTypes.values();
    for (ExpectedHighlightingSet highlightingSet : expectedHighlights) {
      if (highlightingSet.severity != info.getSeverity()) continue;
      if (!highlightingSet.enabled) return true;
      Set<HighlightInfo> infos = highlightingSet.infos;
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
      (Comparing.strEqual(ANY_TEXT, expectedInfo.getDescription()) ||
       Comparing.strEqual(info.getDescription(), expectedInfo.getDescription())) &&
      (expectedInfo.forcedTextAttributes == null ||
       Comparing.equal(expectedInfo.getTextAttributes(null, null), info.getTextAttributes(null, null))) &&
      (expectedInfo.forcedTextAttributesKey == null || expectedInfo.forcedTextAttributesKey.equals(info.forcedTextAttributesKey));
  }

  private static String rangeString(String text, int startOffset, int endOffset) {
    int startLine = StringUtil.offsetToLineNumber(text, startOffset);
    int endLine = StringUtil.offsetToLineNumber(text, endOffset);
    int startCol = startOffset - StringUtil.lineColToOffset(text, startLine, 0);
    int endCol = endOffset - StringUtil.lineColToOffset(text, endLine, 0);
    if (startLine == endLine) {
      return String.format("(%d:%d/%d)", startLine + 1, startCol + 1, endCol - startCol);
    }
    else {
      return String.format("(%d:%d..%d:%d)", startLine + 1, endLine + 1, startCol + 1, endCol + 1);
    }
  }

  private static class MyLineMarkerInfo extends LineMarkerInfo<PsiElement> {
    private final String myTooltip;

    public MyLineMarkerInfo(PsiElement element, TextRange range, int updatePass, GutterIconRenderer.Alignment alignment, String tooltip) {
      super(element, range, null, updatePass, null, null, alignment);
      myTooltip = tooltip;
    }

    @Override
    public String getLineMarkerTooltip() {
      return myTooltip;
    }
  }
}