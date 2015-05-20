package com.jetbrains.javascript.debugger;

import com.google.common.base.CharMatcher;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.sourcemap.MappingEntry;
import org.jetbrains.debugger.sourcemap.MappingList;
import org.jetbrains.debugger.sourcemap.SourceMap;

import java.util.Map;

import static org.jetbrains.rpc.CommandProcessor.LOG;

public final class NameMapper {
  public static final String S1 = ",()[]{}=";
  private static final CharMatcher NAME_TRIMMER = CharMatcher.INVISIBLE.or(CharMatcher.anyOf(S1 + ".&:"));
  // don't trim trailing .&: - could be part of expression
  private static final CharMatcher OPERATOR_TRIMMER = CharMatcher.INVISIBLE.or(CharMatcher.anyOf(S1));

  private final Document document;
  private final Document generatedDocument;
  private final MappingList sourceMappings;
  private final SourceMap sourceMap;

  private Map<String, String> rawNameToSource;

  public NameMapper(@NotNull Document document, @NotNull Document generatedDocument, @NotNull MappingList sourceMappings, @NotNull SourceMap sourceMap) {
    this.document = document;
    this.generatedDocument = generatedDocument;
    this.sourceMappings = sourceMappings;
    this.sourceMap = sourceMap;
  }

  @Nullable
  public Map<String, String> getRawNameToSource() {
    return rawNameToSource;
  }

  // PsiNamedElement, JSVariable for example
  // returns generated name
  @Nullable
  public String map(@NotNull PsiElement identifierOrNamedElement) {
    int offset = identifierOrNamedElement.getTextOffset();
    int line = document.getLineNumber(offset);

    int sourceEntryIndex = sourceMappings.indexOf(line, offset - document.getLineStartOffset(line));
    if (sourceEntryIndex == -1) {
      return null;
    }

    MappingEntry sourceEntry = sourceMappings.get(sourceEntryIndex);
    MappingEntry next = sourceMappings.getNextOnTheSameLine(sourceEntryIndex, false);
    if (next != null && sourceMappings.getColumn(next) == sourceMappings.getColumn(sourceEntry)) {
      warnSeveralMapping(identifierOrNamedElement);
      return null;
    }

    String sourceEntryName = sourceEntry.getName();
    String generatedName = trimName(getGeneratedName(generatedDocument, sourceMap, sourceEntry), true);
    if (!generatedName.isEmpty()) {
      String sourceName = sourceEntryName;
      if (sourceName == null) {
        sourceName = identifierOrNamedElement instanceof PsiNamedElement ? ((PsiNamedElement)identifierOrNamedElement).getName() : identifierOrNamedElement.getText();
        if (sourceName == null) {
          return null;
        }
      }

      if (rawNameToSource == null) {
        rawNameToSource = new THashMap<String, String>();
      }
      rawNameToSource.put(generatedName, sourceName);
      return generatedName;
    }
    return null;
  }

  public static void warnSeveralMapping(@NotNull PsiElement element) {
    // see https://dl.dropboxusercontent.com/u/43511007/s/Screen%20Shot%202015-01-21%20at%2020.33.44.png
    // var1 mapped to the whole "var c, notes, templates, ..." expression text + unrelated text "   ;"
    LOG.warn("incorrect sourcemap, several mappings for named element " + element);
  }

  @NotNull
  public static String trimName(@NotNull CharSequence rawGeneratedName, boolean isLastToken) {
    String generatedName = (isLastToken ? NAME_TRIMMER : OPERATOR_TRIMMER).trimFrom(rawGeneratedName);
    // GWT - button_0_g$ = new Button_5_g$('Click me');
    // so, we should remove all after "="
    int i = generatedName.indexOf('=');
    return i > 0 ? NAME_TRIMMER.trimFrom(generatedName.substring(0, i)) : generatedName;
  }

  @NotNull
  private static CharSequence getGeneratedName(@NotNull Document document, @NotNull SourceMap sourceMap, @NotNull MappingEntry sourceEntry) {
    int lineStartOffset = document.getLineStartOffset(sourceEntry.getGeneratedLine());
    MappingEntry nextGeneratedMapping = sourceMap.getMappings().getNextOnTheSameLine(sourceEntry);
    int endOffset;
    if (nextGeneratedMapping == null) {
      endOffset = document.getLineEndOffset(sourceEntry.getGeneratedLine());
    }
    else {
      endOffset = lineStartOffset + nextGeneratedMapping.getGeneratedColumn();
    }
    return document.getImmutableCharSequence().subSequence(lineStartOffset + sourceEntry.getGeneratedColumn(), endOffset);
  }
}
