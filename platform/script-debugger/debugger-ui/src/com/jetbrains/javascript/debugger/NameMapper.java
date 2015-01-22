package com.jetbrains.javascript.debugger;

import com.google.common.base.CharMatcher;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiNamedElement;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.sourcemap.MappingEntry;
import org.jetbrains.debugger.sourcemap.MappingList;
import org.jetbrains.debugger.sourcemap.SourceMap;

import java.util.Map;

public final class NameMapper {
  public static final String S1 = ",()[]{}=";
  private static final CharMatcher NAME_TRIMMER = CharMatcher.INVISIBLE.or(CharMatcher.anyOf(S1 + ".&"));
  // don't trim trailing .& - could be part of expression
  private static final CharMatcher OPERATOR_TRIMMER = CharMatcher.INVISIBLE.or(CharMatcher.anyOf(S1));

  private final Document document;
  private final Document generatedDocument;
  private final MappingList sourceMappings;
  private final SourceMap sourceMap;

  private Map<String, String> nameMappings;

  NameMapper(@NotNull Document document, @NotNull Document generatedDocument, @NotNull MappingList sourceMappings, @NotNull SourceMap sourceMap) {
    this.document = document;
    this.generatedDocument = generatedDocument;
    this.sourceMappings = sourceMappings;
    this.sourceMap = sourceMap;
  }

  @Nullable
  public Map<String, String> getNameMappings() {
    return nameMappings;
  }

  // JSVariable for example
  public void map(@NotNull PsiNamedElement variable) {
    int offset = variable.getTextOffset();
    int line = document.getLineNumber(offset);
    MappingEntry sourceEntry = sourceMappings.get(line, offset - document.getLineStartOffset(line));
    String sourceEntryName = sourceEntry == null ? null : sourceEntry.getName();
    if (sourceEntry != null) {
      String generatedName = trimName(getGeneratedName(generatedDocument, sourceMap, sourceEntry), true);
      if (!generatedName.isEmpty()) {
        String sourceName = sourceEntryName;
        if (sourceName == null) {
          sourceName = variable.getName();
          if (sourceName == null) {
            return;
          }
        }

        if (nameMappings == null) {
          nameMappings = new THashMap<String, String>();
        }
        nameMappings.put(generatedName, sourceName);
      }
    }
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
