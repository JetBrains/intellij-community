package org.jetbrains.debugger.sourcemap;

import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.Url;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SourceMap {
  private final MappingList mappings;
  final MappingList[] sourceIndexToMappings;

  private final String outFile;

  private final SourceResolver sourceResolver;

  // sources - is not originally specified, but canonicalized/normalized
  public SourceMap(@Nullable String outFile,
                   @NotNull MappingList mappings,
                   @NotNull MappingList[] sourceIndexToMappings,
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

  public MappingList getMappings() {
    return mappings;
  }

  public boolean processMappingsInLine(@NotNull List<Url> sourceUrls,
                                       int sourceLine,
                                       @NotNull Processor<MappingEntry> mappingProcessor,
                                       @Nullable VirtualFile sourceFile,
                                       @Nullable NullableLazyValue<SourceResolver.Resolver> resolver) {
    MappingList mappings = sourceResolver.findMappings(sourceUrls, this, sourceFile);
    if (mappings == null) {
      if (resolver != null) {
        SourceResolver.Resolver resolverValue = resolver.getValue();
        if (resolverValue != null) {
          mappings = sourceResolver.findMappings(this, resolverValue);
        }
      }
      if (mappings == null) {
        return false;
      }
    }
    return mappings.processMappingsInLine(sourceLine, mappingProcessor);
  }

  @NotNull
  public MappingList getMappingsOrderedBySource(int source) {
    return sourceIndexToMappings[source];
  }
}