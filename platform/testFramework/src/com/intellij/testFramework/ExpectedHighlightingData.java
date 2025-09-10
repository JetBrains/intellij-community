// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.LineColumn;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.testFramework.core.FileComparisonFailedError;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.util.DocumentUtil;
import com.intellij.util.MathUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.xml.util.XmlStringUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.intellij.openapi.util.Pair.pair;
import static java.util.Comparator.comparingInt;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Extracts the markers for the expected highlighting ranges, such as {@code <error descr="..."/>},
 * and removes them from the document.
 * <p>
 * Whether warnings, weak warnings and info are checked depends on the constructor.
 * In particular, if the document contains markers whose type is disabled,
 * these markers are not checked.
 */
public class ExpectedHighlightingData {
  @ApiStatus.Internal
  public static final String EXPECTED_DUPLICATION_MESSAGE =
    "Expected duplication problem. Please remove `ExpectedHighlightingData.expectedDuplicatedHighlighting()` surrounding call, if there is no such problem any more";

  private static final String ERROR_MARKER = CodeInsightTestFixture.ERROR_MARKER;
  private static final String WARNING_MARKER = CodeInsightTestFixture.WARNING_MARKER;
  private static final String WEAK_WARNING_MARKER = CodeInsightTestFixture.WEAK_WARNING_MARKER;
  private static final String INFO_MARKER = CodeInsightTestFixture.INFO_MARKER;
  private static final String TEXT_ATTRIBUTES_MARKER = CodeInsightTestFixture.TEXT_ATTRIBUTES_MARKER;
  private static final String END_LINE_HIGHLIGHT_MARKER = CodeInsightTestFixture.END_LINE_HIGHLIGHT_MARKER;
  private static final String END_LINE_WARNING_MARKER = CodeInsightTestFixture.END_LINE_WARNING_MARKER;
  private static final String INJECT_MARKER = "inject";
  private static final String HIGHLIGHT_MARKER = "highlight";
  private static final String INJECTED_SYNTAX_MARKER = "injectedSyntax";
  private static final String SYMBOL_NAME_MARKER = "symbolName";
  private static final String LINE_MARKER = "lineMarker";
  private static final String ANY_TEXT = "*";
  private static class Holder {
    private static final HighlightInfoType WHATEVER = new HighlightInfoType.HighlightInfoTypeImpl(HighlightSeverity.INFORMATION, HighlighterColors.TEXT);
  }

  private static boolean isDuplicatedCheckDisabled = false;
  private static int failedDuplicationChecks = 0;

  public static class ExpectedHighlightingSet {
    private final HighlightSeverity severity;
    private final boolean endOfLine;
    private final boolean enabled;
    private final Set<HighlightInfo> infos;

    public ExpectedHighlightingSet(@NotNull HighlightSeverity severity, boolean endOfLine, boolean enabled) {
      this.severity = severity;
      this.endOfLine = endOfLine;
      this.enabled = enabled;
      this.infos = new HashSet<>();
    }
  }

  private final Map<String, ExpectedHighlightingSet> myHighlightingTypes = new LinkedHashMap<>();
  private final Map<RangeMarker, LineMarkerInfo<?>> myLineMarkerInfos = new HashMap<>();
  private final Document myDocument;
  private final String myText;
  private final boolean myIgnoreExtraHighlighting;

  public ExpectedHighlightingData(@NotNull Document document, boolean checkWarnings, boolean checkInfos) {
    this(document, checkWarnings, false, checkInfos);
  }

  public ExpectedHighlightingData(@NotNull Document document, boolean checkWarnings, boolean checkWeakWarnings, boolean checkInfos) {
    this(document, checkWarnings, checkWeakWarnings, checkInfos, false);
  }

  public ExpectedHighlightingData(@NotNull Document document,
                                  boolean checkWarnings,
                                  boolean checkWeakWarnings,
                                  boolean checkInfos,
                                  boolean ignoreExtraHighlighting) {
    myDocument = document;
    myText = document.getText();

    registerHighlightingType(ERROR_MARKER, new ExpectedHighlightingSet(HighlightSeverity.ERROR, false, true));
    registerHighlightingType(WARNING_MARKER, new ExpectedHighlightingSet(HighlightSeverity.WARNING, false, false));
    registerHighlightingType(WEAK_WARNING_MARKER, new ExpectedHighlightingSet(HighlightSeverity.WEAK_WARNING, false, false));
    registerHighlightingType(INJECT_MARKER, new ExpectedHighlightingSet(HighlightInfoType.INJECTED_FRAGMENT_SEVERITY, false, false));
    registerHighlightingType(HIGHLIGHT_MARKER, new ExpectedHighlightingSet(HighlightInfoType.HIGHLIGHTED_REFERENCE_SEVERITY, false, false));
    registerHighlightingType(INJECTED_SYNTAX_MARKER, new ExpectedHighlightingSet(HighlightInfoType.INJECTED_FRAGMENT_SYNTAX_SEVERITY, false, false));
    registerHighlightingType(INFO_MARKER, new ExpectedHighlightingSet(HighlightSeverity.INFORMATION, false, false));
    registerHighlightingType(TEXT_ATTRIBUTES_MARKER, new ExpectedHighlightingSet(HighlightSeverity.TEXT_ATTRIBUTES, false, false));
    registerHighlightingType(SYMBOL_NAME_MARKER, new ExpectedHighlightingSet(HighlightInfoType.SYMBOL_TYPE_SEVERITY, false, false));
    for (SeveritiesProvider provider : SeveritiesProvider.EP_NAME.getExtensionList()) {
      for (HighlightInfoType type : provider.getSeveritiesHighlightInfoTypes()) {
        HighlightSeverity severity = type.getSeverity(null);
        registerHighlightingType(severity.getName(), new ExpectedHighlightingSet(severity, false, true));
      }
    }
    registerHighlightingType(END_LINE_HIGHLIGHT_MARKER, new ExpectedHighlightingSet(HighlightSeverity.ERROR, true, true));
    registerHighlightingType(END_LINE_WARNING_MARKER, new ExpectedHighlightingSet(HighlightSeverity.WARNING, true, false));
    myIgnoreExtraHighlighting = ignoreExtraHighlighting;
    if (checkWarnings) checkWarnings();
    if (checkWeakWarnings) checkWeakWarnings();
    if (checkInfos) checkInfos();
  }

  public ExpectedHighlightingData(@NotNull Document document) {
    this(document, false, false, false, false);
  }

  public boolean hasLineMarkers() {
    return !myLineMarkerInfos.isEmpty();
  }

  public void init() {
    WriteCommandAction.runWriteCommandAction(null, () -> {
      DocumentUtil.executeInBulk(myDocument, () -> {
        extractExpectedLineMarkerSet(myDocument);
        extractExpectedHighlightsSet(myDocument);
      });
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
    registerHighlightingType(TEXT_ATTRIBUTES_MARKER, new ExpectedHighlightingSet(HighlightSeverity.TEXT_ATTRIBUTES, false, true));
    registerHighlightingType(INJECT_MARKER, new ExpectedHighlightingSet(HighlightInfoType.INJECTED_FRAGMENT_SEVERITY, false, true));
    registerHighlightingType(HIGHLIGHT_MARKER, new ExpectedHighlightingSet(HighlightInfoType.HIGHLIGHTED_REFERENCE_SEVERITY, false, true));
  }

  public void checkSymbolNames() {
    registerHighlightingType(SYMBOL_NAME_MARKER, new ExpectedHighlightingSet(HighlightInfoType.SYMBOL_TYPE_SEVERITY, false, true));
  }

  public void registerHighlightingType(@NotNull String key, @NotNull ExpectedHighlightingSet highlightingSet) {
    myHighlightingTypes.put(key, highlightingSet);
  }

  private void refreshLineMarkers() {
    for (Map.Entry<RangeMarker, LineMarkerInfo<?>> entry : myLineMarkerInfos.entrySet()) {
      RangeMarker rangeMarker = entry.getKey();
      int startOffset = rangeMarker.getStartOffset();
      int endOffset = rangeMarker.getEndOffset();
      LineMarkerInfo<?> value = entry.getValue();
      TextRange range = new TextRange(startOffset, endOffset);
      String tooltip = value.getLineMarkerTooltip();
      Icon icon = value.getIcon();
      MyLineMarkerInfo markerInfo = new MyLineMarkerInfo(range, GutterIconRenderer.Alignment.RIGHT, tooltip, icon != null ? icon.toString() : null);
      entry.setValue(markerInfo);
    }
  }

  private void extractExpectedLineMarkerSet(Document document) {
    String text = document.getText();

    String pat = ".*?((<" + LINE_MARKER + ")(?: descr=\"((?:[^\"\\\\]|\\\\\")*)\")?(?: icon=\"((?:[^\"\\\\]|\\\\\")*)\")?>)(.*)";
    Pattern openingTagRx = Pattern.compile(pat, Pattern.DOTALL);
    Pattern closingTagRx = Pattern.compile("(.*?)(</" + LINE_MARKER + ">)(.*)", Pattern.DOTALL);

    while (true) {
      Matcher opening = openingTagRx.matcher(text);
      if (!opening.matches()) break;

      int startOffset = opening.start(1);
      String descr = opening.group(3) != null ? opening.group(3) : ANY_TEXT;
      String icon = opening.group(4) != null ? opening.group(4) : ANY_TEXT;
      String rest = opening.group(5);

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

      TextRange range = new TextRange(startOffset, endOffset);
      String tooltip = StringUtil.unescapeStringCharacters(descr);
      LineMarkerInfo<PsiElement> markerInfo =
        new MyLineMarkerInfo(range, GutterIconRenderer.Alignment.RIGHT, tooltip, icon);
      myLineMarkerInfos.put(document.createRangeMarker(startOffset, endOffset), markerInfo);

      text = document.getText();
    }
  }

  /**
   * Removes highlights (bounded with {@code <marker>...</marker>}) from test case file.
   */
  private void extractExpectedHighlightsSet(Document document) {
    String text = document.getText();

    Set<String> markers = myHighlightingTypes.keySet();
    String typesRx = String.join("|", markers);
    String openingTagRx = "<(?<marker>" + typesRx + ")" +
                          "(?:\\s+" +
                          "(?:descr=\"(?<descr>(?:\\\\\"|[^\"])*)\"" +
                          "|type=\"(?<type>[0-9A-Z_]+)\"" +
                          "|foreground=\"(?<foreground>[0-9xa-f]+)\"" +
                          "|background=\"(?<background>[0-9xa-f]+)\"" +
                          "|effectcolor=\"(?<effectcolor>[0-9xa-f]+)\"" +
                          "|effecttype=\"(?<effecttype>[A-Z]+)\"" +
                          "|fonttype=\"(?<fonttype>[0-9]+)\"" +
                          "|textAttributesKey=\"(?<textAttributesKey>(?:\\\\\"|[^\"])*)\"" +
                          "|tooltip=\"(?<tooltip>(?:\\\\\"|[^\"])*)\"" +
                          "))*" +
                          "\\s*(?<closed>/)?>";

    Matcher matcher = Pattern.compile(openingTagRx).matcher(text);
    Ref<Integer> textOffset = Ref.create(0);
    int pos = 0;
    while (matcher.find(pos)) {
      textOffset.set(textOffset.get() + matcher.start() - pos);
      pos = extractExpectedHighlight(matcher, text, document, textOffset);
    }
  }

  private int extractExpectedHighlight(Matcher matcher, String text, Document document, Ref<Integer> textOffset) {
    document.deleteString(textOffset.get(), textOffset.get() + matcher.end() - matcher.start());

    String marker = matcher.group("marker");
    @NlsSafe String descr = matcher.group("descr");
    String typeString = matcher.group("type");
    String foregroundColor = matcher.group("foreground");
    String backgroundColor = matcher.group("background");
    String effectColor = matcher.group("effectcolor");
    String effectType = matcher.group("effecttype");
    String fontType = matcher.group("fonttype");
    String attrKey = matcher.group("textAttributesKey");
    @NlsSafe String tooltip = matcher.group("tooltip");
    boolean closed = matcher.group("closed") != null;

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

    if (tooltip == null) {
      tooltip = ANY_TEXT;  // no tooltip any string by default
    }
    else if (tooltip.equals("null")) {
      tooltip = null;  // explicit "null" tooltip
    }
    if (tooltip != null) {
      tooltip = tooltip.replaceAll("\\\\\\\\\"", "\"");  // replace: \\" to ", doesn't check symbol before sequence \\"
      tooltip = tooltip.replaceAll("\\\\\"", "\"");
    }

    HighlightInfoType type = Holder.WHATEVER;
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
      @JdkConstants.FontStyle int ft = fontType != null ? Integer.parseInt(fontType) : 0;
      forcedAttributes = new TextAttributes(
        Color.decode(foregroundColor), backgroundColor != null ? Color.decode(backgroundColor) : null,
        effectColor != null ? Color.decode(effectColor) : null,
        effectType != null ? EffectType.valueOf(effectType) : null, ft);
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
      if (descr != null) {
        builder.description(descr);
      }
      if (tooltip != null) {
        if (tooltip.startsWith("<html>")) {
          builder.escapedToolTip(tooltip);
        }
        else {
          builder.unescapedToolTip(tooltip);
        }
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

  public void checkLineMarkers(@Nullable PsiFile psiFile, @NotNull Collection<? extends LineMarkerInfo> markerInfos, @NotNull String text) {
    String fileName = psiFile == null ? "" : psiFile.getName() + ": ";
    StringBuilder failMessage = new StringBuilder();

    for (LineMarkerInfo info : markerInfos) {
      if (!containsLineMarker(info, myLineMarkerInfos.values())) {
        if (!failMessage.isEmpty()) failMessage.append('\n');
        failMessage.append(fileName).append("extra ")
          .append(rangeString(text, info.startOffset, info.endOffset))
          .append(": '").append(sanitizedLineMarkerTooltip(info)).append('\'');
        Icon icon = info.getIcon();
        if (icon != null && !icon.toString().equals(ANY_TEXT)) {
          failMessage.append(" icon='").append(icon).append('\'');
        }
      }
    }

    for (LineMarkerInfo expectedLineMarker : myLineMarkerInfos.values()) {
      if (markerInfos.isEmpty() || !containsLineMarker(expectedLineMarker, markerInfos)) {
        if (!failMessage.isEmpty()) failMessage.append('\n');
        failMessage.append(fileName).append("missing ")
          .append(rangeString(text, expectedLineMarker.startOffset, expectedLineMarker.endOffset))
          .append(": '").append(sanitizedLineMarkerTooltip(expectedLineMarker)).append('\'');
        Icon icon = expectedLineMarker.getIcon();
        if (icon != null && !icon.toString().equals(ANY_TEXT)) {
          failMessage.append(" icon='").append(icon).append('\'');
        }
      }
    }

    if (!failMessage.isEmpty()) {
      String filePath = null;
      if (psiFile != null) {
        VirtualFile file = psiFile.getVirtualFile();
        if (file != null) {
          filePath = file.getUserData(VfsTestUtil.TEST_DATA_FILE_PATH);
        }
      }
      throw new FileComparisonFailedError(failMessage.toString(), myText, getActualLineMarkerFileText(markerInfos), filePath);
    }
  }

  protected String sanitizedLineMarkerTooltip(@NotNull LineMarkerInfo info) {
    return info.getLineMarkerTooltip();
  }

  private @NotNull String getActualLineMarkerFileText(@NotNull Collection<? extends LineMarkerInfo> markerInfos) {
    StringBuilder result = new StringBuilder();
    int index = 0;
    List<Pair<LineMarkerInfo, Integer>> lineMarkerInfos = new ArrayList<>(markerInfos.size() * 2);
    for (LineMarkerInfo info : markerInfos) lineMarkerInfos.add(Pair.create(info, info.startOffset));
    for (LineMarkerInfo info : markerInfos) lineMarkerInfos.add(Pair.create(info, info.endOffset));
    Collections.reverse(lineMarkerInfos.subList(markerInfos.size(), lineMarkerInfos.size()));
    lineMarkerInfos.sort(comparingInt(o -> o.second));
    String documentText = myDocument.getText();
    for (Pair<LineMarkerInfo, Integer> info : lineMarkerInfos) {
      LineMarkerInfo expectedLineMarker = info.first;
      result.append(documentText, index, info.second);
      if (info.second == expectedLineMarker.startOffset) {
        result
          .append("<lineMarker descr=\"")
          .append(sanitizedLineMarkerTooltip(expectedLineMarker))
          .append("\">");
      }
      else {
        result.append("</lineMarker>");
      }
      index = info.second;
    }
    result.append(documentText, index, myDocument.getTextLength());
    return result.toString();
  }

  protected boolean containsLineMarker(@NotNull LineMarkerInfo info, Collection<? extends LineMarkerInfo> where) {
    Icon icon = info.getIcon();
    for (LineMarkerInfo markerInfo : where) {
      if (markerInfo.startOffset == info.startOffset &&
          markerInfo.endOffset == info.endOffset &&
          matchLineMarkersTooltip(info, markerInfo) &&
          matchIcons(icon, markerInfo.getIcon())) {
        return true;
      }
    }

    return false;
  }

  protected boolean matchLineMarkersTooltip(@NotNull LineMarkerInfo info, @NotNull LineMarkerInfo markerInfo) {
    return matchDescriptions(false, info.getLineMarkerTooltip(), markerInfo.getLineMarkerTooltip());
  }

  protected static boolean matchIcons(Icon icon1, Icon icon2) {
    String s1 = String.valueOf(icon1);
    String s2 = String.valueOf(icon2);
    if (Comparing.strEqual(s1, s2)) return true;
    return Comparing.strEqual(ANY_TEXT, s1) || Comparing.strEqual(ANY_TEXT, s2);
  }

  public void checkResult(@Nullable PsiFile psiFile, Collection<? extends HighlightInfo> infos, String text) {
    checkResult(psiFile, infos, text, null);
  }

  public void checkResult(@Nullable PsiFile psiFile, Collection<? extends HighlightInfo> infos, String text, @Nullable String filePath) {
    StringBuilder failMessage = new StringBuilder();

    Set<HighlightInfo> expectedFound = CollectionFactory.createCustomHashingStrategySet(new HashingStrategy<>() {
      @Override
      public int hashCode(HighlightInfo object) {
        return object.hashCode();
      }

      @Override
      public boolean equals(HighlightInfo o1, HighlightInfo o2) {
        return o1==null||o2==null?o1==o2:o1.getSeverity()==o2.getSeverity()&&haveSamePresentation(o1, o2, true);
      }
    });
    if (!myIgnoreExtraHighlighting) {
      Map<ExpectedHighlightingSet, Set<HighlightInfo>> indexed = new HashMap<>();
      for (ExpectedHighlightingSet set : myHighlightingTypes.values()) {
        indexed.put(set, indexInfos(set.infos));
      }

      for (HighlightInfo info : infos) {
        ThreeState state = expectedInfosContainsInfo(info, indexed);
        if (state == ThreeState.NO) {
          reportProblem(psiFile, failMessage, text, info, "extra ");
          failMessage.append(" [").append(info.type).append(']');
        }
        else if (state == ThreeState.YES) {
          if (expectedFound.contains(info)) {
            if (isDuplicatedCheckDisabled) {
              //noinspection AssignmentToStaticFieldFromInstanceMethod
              failedDuplicationChecks++;
            }
            else {
              reportProblem(psiFile, failMessage, text, info, "duplicated ");
            }
          }
          expectedFound.add(info);
        }
      }
    }

    Set<HighlightInfo> indexedInfos = indexInfos(infos);
    Collection<ExpectedHighlightingSet> expectedHighlights = myHighlightingTypes.values();
    for (ExpectedHighlightingSet highlightingSet : expectedHighlights) {
      Set<HighlightInfo> expInfos = highlightingSet.infos;
      for (HighlightInfo expectedInfo : expInfos) {
        if (!indexedInfos.contains(expectedInfo) && highlightingSet.enabled) {
          reportProblem(psiFile, failMessage, text, expectedInfo, "missing ");
        }
      }
    }

    if (!failMessage.isEmpty()) {
      if (filePath == null && psiFile != null) {
        VirtualFile file = psiFile.getVirtualFile();
        if (file != null) {
          filePath = file.getUserData(VfsTestUtil.TEST_DATA_FILE_PATH);
        }
      }

      failMessage.append('\n');
      compareTexts(infos, text, failMessage.toString(), filePath);
    }
  }

  private static @NotNull Set<HighlightInfo> indexInfos(Collection<? extends HighlightInfo> infos) {
    Set<HighlightInfo> index = CollectionFactory.createCustomHashingStrategySet(new HashingStrategy<>() {
      @Override
      public int hashCode(HighlightInfo object) {
        return object == null ? 0 : Objects.hash(object.startOffset, object.endOffset); //good enough
      }

      @Override
      public boolean equals(HighlightInfo o1, HighlightInfo o2) {
        return o1==null || o2 == null ? o1 == o2 : matchesPattern(o1, o2, false);
      }
    });
    index.addAll(infos);
    return index;
  }

  private static void reportProblem(@Nullable PsiFile psiFile,
                                    @NotNull StringBuilder failMessage,
                                    @NotNull String text,
                                    @NotNull HighlightInfo info,
                                    @NotNull String messageType) {
    String fileName = psiFile == null ? "" : psiFile.getName() + ": ";
    int startOffset = info.getActualStartOffset();
    int endOffset = info.getActualEndOffset();
    String s = text.substring(startOffset, endOffset);
    String desc = info.getDescription();

    if (!failMessage.isEmpty()) {
      failMessage.append('\n');
    }
    failMessage.append(fileName).append(messageType)
      .append(rangeString(text, startOffset, endOffset))
      .append(": '").append(s).append('\'');
    if (desc != null) {
      failMessage.append(" (").append(desc).append(')');
    }
  }

  private void compareTexts(Collection<? extends HighlightInfo> infos, String text, String failMessage, @Nullable String filePath) {
    String actual = composeText(myHighlightingTypes, infos, text);
    if (filePath != null && !myText.equals(actual)) {
      // uncomment to overwrite, don't forget to revert on commit!
      //VfsTestUtil.overwriteTestData(filePath, actual);
      //return;
      throw new FileComparisonFailedError(failMessage, myText, actual, filePath);
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

  public static @NotNull String composeText(@NotNull Map<String, ExpectedHighlightingSet> types,
                                            @NotNull Collection<? extends HighlightInfo> infos,
                                            @NotNull String text) {
    // filter highlighting data and map each highlighting to a tag name
    List<Pair<String, ? extends HighlightInfo>> list = infos.stream()
      .map(info -> pair(findTag(types, info), info))
      .filter(p -> p.first != null)
      .collect(Collectors.toList());
    boolean showAttributesKeys =
      types.values().stream().flatMap(set -> set.infos.stream()).anyMatch(info -> info.forcedTextAttributesKey != null);

    String anyWrappedInHtml = XmlStringUtil.wrapInHtml(ANY_TEXT);
    boolean showTooltips =
      types.values().stream().flatMap(set -> set.infos.stream()).anyMatch(info -> !anyWrappedInHtml.equals(info.getToolTip()));

    // sort filtered highlighting data by end offset in descending order
    list.sort((o1, o2) -> {
      HighlightInfo i1 = o1.second;
      HighlightInfo i2 = o2.second;

      int byEnds = i2.endOffset - i1.endOffset;
      if (byEnds != 0) return byEnds;

      if (!i1.isAfterEndOfLine() && !i2.isAfterEndOfLine()) {
        int byStarts = i1.startOffset - i2.startOffset;
        if (byStarts != 0) return byStarts;
      }
      else {
        int byEOL = Boolean.compare(i2.isAfterEndOfLine(), i1.isAfterEndOfLine());
        if (byEOL != 0) return byEOL;
      }

      int bySeverity = i2.getSeverity().compareTo(i1.getSeverity());
      if (bySeverity != 0) return bySeverity;

      return Comparing.compare(i1.getDescription(), i2.getDescription());
    });

    // combine highlighting data with original text
    StringBuilder sb = new StringBuilder();
    int[] offsets = composeText(sb, list, 0, text, text.length(), -1, showAttributesKeys, showTooltips);
    sb.insert(0, text.substring(0, offsets[1]));
    return sb.toString();
  }

  /**
   * @deprecated Consider to rework your architecture and fix double registration of same highlighting information
   */
  @Deprecated
  public static void expectedDuplicatedHighlighting(@NotNull Runnable check) {
    try {
      isDuplicatedCheckDisabled = true;
      failedDuplicationChecks = 0;
      check.run();
    }
    finally {
      isDuplicatedCheckDisabled = false;
    }
    if (failedDuplicationChecks == 0) {
      throw new IllegalStateException(EXPECTED_DUPLICATION_MESSAGE);
    }
  }

  private static int[] composeText(StringBuilder sb,
                                   List<? extends Pair<String, ? extends HighlightInfo>> list, int index,
                                   String text, int endPos, int startPos,
                                   boolean showAttributesKeys,
                                   boolean showTooltip) {
    int i = index;
    while (i < list.size()) {
      Pair<String, ? extends HighlightInfo> pair = list.get(i);
      HighlightInfo info = pair.second;
      if (info.endOffset <= startPos) {
        break;
      }

      String severity = pair.first;
      HighlightInfo prev = i < list.size() - 1 ? list.get(i + 1).second : null;

      int start = MathUtil.clamp(info.endOffset, 0, text.length());
      int end = MathUtil.clamp(endPos, start, text.length());
      sb.insert(0, text.substring(start, end));
      sb.insert(0, "</" + severity + '>');
      endPos = info.endOffset;
      if (prev != null && prev.endOffset > info.startOffset) {
        int[] offsets = composeText(sb, list, i + 1, text, endPos, info.startOffset, showAttributesKeys, showTooltip);
        i = offsets[0] - 1;
        endPos = offsets[1];
      }
      sb.insert(0, text.substring(info.startOffset, Math.max(endPos,info.startOffset)));

      StringBuilder str = new StringBuilder().append('<').append(severity);

      if (info.getSeverity() != HighlightInfoType.HIGHLIGHTED_REFERENCE_SEVERITY) {
        String description = info.getDescription();
        String toolTip = info.getToolTip();
        str.append(" descr=\"").append(StringUtil.escapeQuotes(String.valueOf(description))).append('"');
        if (showTooltip) {
          str.append(" tooltip=\"").append(StringUtil.escapeQuotes(String.valueOf(toolTip != null ? XmlStringUtil.stripHtml(toolTip) : null))).append('"');
        }
      }
      if (showAttributesKeys) {
        str.append(" textAttributesKey=\"").append(ObjectUtils.notNull(info.forcedTextAttributesKey, info.type.getAttributesKey()).getExternalName()).append('"');
      }
      str.append('>');
      sb.insert(0, str);

      endPos = info.startOffset;
      i++;
    }

    return new int[]{i, endPos};
  }

  private ThreeState expectedInfosContainsInfo(HighlightInfo info, Map<ExpectedHighlightingSet, Set<HighlightInfo>> indexed) {
    if (info.getTextAttributes(null, null) == TextAttributes.ERASE_MARKER) return ThreeState.UNSURE;
    Collection<ExpectedHighlightingSet> expectedHighlights = myHighlightingTypes.values();
    for (ExpectedHighlightingSet highlightingSet : expectedHighlights) {
      if (highlightingSet.severity != info.getSeverity()) continue;
      if (!highlightingSet.enabled) return ThreeState.UNSURE;
      Set<HighlightInfo> index = indexed.get(highlightingSet);
      if (index != null && index.contains(info)) return ThreeState.YES;
    }
    return ThreeState.NO;
  }

  private static boolean matchesPattern(@NotNull HighlightInfo expectedInfo, @NotNull HighlightInfo info, boolean strictMatch) {
    if (expectedInfo == info) return true;
    boolean typeMatches = expectedInfo.type.equals(info.type) || !strictMatch && (expectedInfo.type == Holder.WHATEVER || info.type == Holder.WHATEVER);
    boolean textAttributesMatches = sameTextAttributesByValue(expectedInfo.getTextAttributes(null, null), info.getTextAttributes(null, null)) ||
                                    !strictMatch && (expectedInfo.forcedTextAttributes == null || info.forcedTextAttributes == null);
    boolean attributesKeyMatches = !strictMatch && (expectedInfo.forcedTextAttributesKey == null || info.forcedTextAttributesKey == null) ||
                                   Objects.equals(expectedInfo.forcedTextAttributesKey, info.forcedTextAttributesKey);
    return
      haveSamePresentation(info, expectedInfo, strictMatch) &&
      info.getSeverity() == expectedInfo.getSeverity() &&
      typeMatches &&
      textAttributesMatches &&
      attributesKeyMatches;
  }

  /**
   * Compare using the resulting presentation, ignoring differences in used keys
   */
  public static boolean sameTextAttributesByValue(@Nullable TextAttributes expected, @Nullable TextAttributes actual) {
    if (expected == null || actual == null) return false;

    if (expected.getFontType() != actual.getFontType()) return false;
    if (!Comparing.equal(expected.getBackgroundColor(), actual.getBackgroundColor())) return false;
    if (!Comparing.equal(expected.getEffectColor(), actual.getEffectColor())) return false;
    if (expected.getEffectType() != actual.getEffectType()) return false;
    if (!Comparing.equal(expected.getErrorStripeColor(), actual.getErrorStripeColor())) return false;
    if (!Comparing.equal(expected.getForegroundColor(), actual.getForegroundColor())) return false;
    if (!Comparing.equal(expected.getAdditionalEffects(), actual.getAdditionalEffects())) return false;
    return true;
  }

  private static boolean haveSamePresentation(@NotNull HighlightInfo info1, @NotNull HighlightInfo info2, boolean strictMatch) {
    return info1.startOffset == info2.startOffset &&
           info1.endOffset == info2.endOffset &&
           info1.isAfterEndOfLine() == info2.isAfterEndOfLine() &&
           matchDescriptions(strictMatch, info1.getDescription(), info2.getDescription()) &&
           matchTooltips(strictMatch, info1.getToolTip(), info2.getToolTip());
  }

  protected static boolean matchDescriptions(boolean strictMatch, String d1, String d2) {
    if (Comparing.strEqual(d1, d2)) return true;
    if (strictMatch) return false;
    if (Comparing.strEqual(ANY_TEXT, d1) || Comparing.strEqual(ANY_TEXT, d2)) return true;
    if (d1 != null && d2 != null) {
      if (d1.endsWith(StringUtil.ELLIPSIS) && d1.regionMatches(0, d2, 0, d1.length() - 1)) {
        return true;
      }
      if (d2.endsWith(StringUtil.ELLIPSIS) && d2.regionMatches(0, d1, 0, d2.length() - 1)) {
        return true;
      }
    }
    return false;
  }

  private static boolean matchTooltips(boolean strictMatch, String t1, String t2) {
    if (Comparing.strEqual(t1, t2)) return true;
    if (strictMatch) return false;
    t1 = t1 != null ? Strings.unescapeXmlEntities(XmlStringUtil.stripHtml(t1)) : null;
    t2 = t2 != null ? Strings.unescapeXmlEntities(XmlStringUtil.stripHtml(t2)) : null;
    return matchDescriptions(false, t1, t2);
  }

  private static String rangeString(String text, int startOffset, int endOffset) {
    LineColumn start = StringUtil.offsetToLineColumn(text, startOffset);
    assert start != null: "textLength = " + text.length() + ", startOffset = " + startOffset;

    LineColumn end = StringUtil.offsetToLineColumn(text, endOffset);
    assert end != null : "textLength = " + text.length() + ", endOffset = " + endOffset;

    if (start.line == end.line) {
      return String.format("(%d:%d/%d at offset %d)", start.line + 1, start.column + 1, end.column - start.column, startOffset);
    }
    else {
      return String.format("(%d:%d..%d:%d at offset %d)", start.line + 1, end.line + 1, start.column + 1, end.column + 1, startOffset);
    }
  }

  private static final SmartPsiElementPointer<PsiElement> NULL_POINTER = new SmartPsiElementPointer<>() {
    @Override
    public @Nullable PsiElement getElement() {
      return null;
    }

    @Override
    public @Nullable PsiFile getContainingFile() {
      return null;
    }

    @Override
    public @NotNull Project getProject() {
      throw new UnsupportedOperationException();
    }

    @Override
    public VirtualFile getVirtualFile() {
      return null;
    }

    @Override
    public @Nullable Segment getRange() {
      return null;
    }

    @Override
    public @Nullable Segment getPsiRange() {
      return null;
    }
  };

  private static class PathIcon implements Icon {
    private final String path;

    private PathIcon(@NotNull String path) {
      this.path = path;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
    }

    @Override
    public int getIconWidth() {
      return 16;
    }

    @Override
    public int getIconHeight() {
      return 16;
    }

    @Override
    public int hashCode() {
      return path.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj || (obj instanceof PathIcon && ((PathIcon)obj).path.equals(path));
    }

    @Override
    public String toString() {
      return path;
    }
  }

  private static class MyLineMarkerInfo extends LineMarkerInfo<PsiElement> {
    private final String myTooltip;

    MyLineMarkerInfo(TextRange range, GutterIconRenderer.Alignment alignment, String tooltip, String iconPath) {
      super(NULL_POINTER, range, new PathIcon(iconPath), null, null, null, alignment);
      myTooltip = tooltip;
    }

    @Override
    public String getLineMarkerTooltip() {
      return myTooltip;
    }
  }
}
