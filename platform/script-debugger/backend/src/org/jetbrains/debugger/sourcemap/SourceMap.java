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
package org.jetbrains.debugger.sourcemap;

import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Url;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SourceMap {
  private final MappingList mappings;
  final MappingList[] sourceIndexToMappings;

  private final String outFile;

  private final SourceResolver sourceResolver;
  private final boolean hasNameMappings;

  // sources - is not originally specified, but canonicalized/normalized
  public SourceMap(@Nullable String outFile,
                   @NotNull MappingList mappings,
                   @NotNull MappingList[] sourceIndexToMappings,
                   @NotNull SourceResolver sourceResolver,
                   boolean hasNameMappings) {
    this.outFile = outFile;
    this.mappings = mappings;
    this.sourceIndexToMappings = sourceIndexToMappings;
    this.sourceResolver = sourceResolver;
    this.hasNameMappings = hasNameMappings;
  }

  public boolean hasNameMappings() {
    return hasNameMappings;
  }

  @NotNull
  public SourceResolver getSourceResolver() {
    return sourceResolver;
  }

  @Nullable
  public String getOutFile() {
    return outFile;
  }

  public Url[] getSources() {
    return sourceResolver.canonicalizedSources;
  }

  @NotNull
  public MappingList getMappings() {
    return mappings;
  }

  public int getSourceLineByRawLocation(int rawLine, int rawColumn) {
    MappingEntry entry = getMappings().get(rawLine, rawColumn);
    return entry == null ? -1: entry.getSourceLine();
  }

  @Nullable
  public MappingList findMappingList(@NotNull List<Url> sourceUrls, @Nullable VirtualFile sourceFile, @Nullable NullableLazyValue<SourceResolver.Resolver> resolver) {
    MappingList mappings = sourceResolver.findMappings(sourceUrls, this, sourceFile);
    if (mappings == null && resolver != null) {
      SourceResolver.Resolver resolverValue = resolver.getValue();
      if (resolverValue != null) {
        mappings = sourceResolver.findMappings(sourceFile, this, resolverValue);
      }
    }
    return mappings;
  }

  public boolean processMappingsInLine(@NotNull List<Url> sourceUrls,
                                       int sourceLine,
                                       @NotNull MappingList.MappingsProcessorInLine mappingProcessor,
                                       @Nullable VirtualFile sourceFile,
                                       @Nullable NullableLazyValue<SourceResolver.Resolver> resolver) {
    MappingList mappings = findMappingList(sourceUrls, sourceFile, resolver);
    return mappings != null && mappings.processMappingsInLine(sourceLine, mappingProcessor);
  }

  @NotNull
  public MappingList getMappingsOrderedBySource(int source) {
    return sourceIndexToMappings[source];
  }
}