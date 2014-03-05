package org.jetbrains.debugger.sourcemap;

import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Url;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SourceMap {
  private final List<MappingEntry> mappings;
  final MappingEntry[][] sourceIndexToMappings;

  private final String outFile;

  private final SourceResolver sourceResolver;

  // sources - is not originally specified, but canonicalized/normalized
  public SourceMap(@Nullable String outFile,
                   @NotNull List<MappingEntry> mappings,
                   @NotNull MappingEntry[][] sourceIndexToMappings,
                   @NotNull SourceResolver sourceResolver) {
    this.outFile = outFile;
    this.mappings = mappings;
    this.sourceIndexToMappings = sourceIndexToMappings;
    this.sourceResolver = sourceResolver;
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

  public List<MappingEntry> getMappings() {
    return mappings;
  }

  @NotNull
  public MappingEntry findEntry(int generatedLine, int generatedColumn) {
    // todo honor Google Chrome bug related to paused location
    return mappings.get(findEntryIndex(generatedLine, generatedColumn));
  }

  public int findEntryIndex(int generatedLine, int generatedColumn) {
    int first = 0;
    int count = mappings.size();
    while (count > 1) {
      int step = count >> 1;
      int middle = first + step;
      MappingEntry mapping = mappings.get(middle);
      if (generatedLine < mapping.getGeneratedLine() ||
          (generatedLine == mapping.getGeneratedLine() && generatedColumn < mapping.getGeneratedColumn())) {
        count = step;
      }
      else {
        first = middle;
        count -= step;
      }
    }
    return first;
  }

  @Nullable
  public MappingEntry findFirstEntryInLine(@NotNull List<Url> sourceUrls, int sourceLine, @Nullable VirtualFile sourceFile, @Nullable NullableLazyValue<SourceResolver.Resolver> resolver) {
    MappingEntry[] entries = sourceResolver.findEntries(sourceUrls, this, sourceFile);
    if (entries == null) {
      if (resolver != null) {
        SourceResolver.Resolver resolverValue = resolver.getValue();
        if (resolverValue != null) {
          entries = sourceResolver.findEntries(this, resolverValue);
        }
      }
      if (entries == null) {
        return null;
      }
    }

    int low = 0;
    int high = entries.length - 1;
    while (low <= high) {
      int middle = (low + high) >>> 1;
      MappingEntry mapping = entries[middle];
      if (sourceLine == mapping.getSourceLine()) {
        // find first
        int firstEntryIndex = middle;
        while (firstEntryIndex > 0 && entries[firstEntryIndex - 1].getSourceLine() == mapping.getSourceLine()) {
          firstEntryIndex--;
        }
        return entries[firstEntryIndex];
      }
      else if (sourceLine > mapping.getSourceLine()) {
        low = middle + 1;
      }
      else {
        high = middle - 1;
      }
    }
    return null;
  }
}