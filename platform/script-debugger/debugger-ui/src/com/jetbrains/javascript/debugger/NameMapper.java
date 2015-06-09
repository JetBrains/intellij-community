/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

public class NameMapper {
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
    String generatedName = extractName(getGeneratedName(generatedDocument, sourceMap, sourceEntry), true);
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
    LOG.warn("incorrect sourcemap, several mappings for named element " + element.getText());
  }

  @NotNull
  public static String trimName(@NotNull CharSequence rawGeneratedName, boolean isLastToken) {
    return (isLastToken ? NAME_TRIMMER : OPERATOR_TRIMMER).trimFrom(rawGeneratedName);
  }

  @NotNull
  protected String extractName(@NotNull CharSequence rawGeneratedName, boolean isLastToken) {
    return trimName(rawGeneratedName, isLastToken);
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