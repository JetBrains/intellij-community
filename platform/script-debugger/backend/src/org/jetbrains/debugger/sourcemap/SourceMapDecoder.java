package org.jetbrains.debugger.sourcemap;

import com.google.gson.stream.JsonToken;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.SmartList;
import com.intellij.util.text.CharSequenceSubSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.JsonReaderEx;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.jetbrains.debugger.sourcemap.Base64VLQ.CharIterator;

// https://docs.google.com/document/d/1U1RGAehQwRypUTovF1KRlpiOFze0b-_2gc6fAH0KY0k/edit?hl=en_US
public final class SourceMapDecoder {
  public static final int UNMAPPED = -1;

  private static final Comparator<MappingEntry> MAPPING_COMPARATOR_BY_SOURCE_POSITION = new Comparator<MappingEntry>() {
    @Override
    public int compare(MappingEntry o1, MappingEntry o2) {
      if (o1.getSourceLine() == o2.getSourceLine()) {
        return o1.getSourceColumn() - o2.getSourceColumn();
      }
      else {
        return o1.getSourceLine() - o2.getSourceLine();
      }
    }
  };

  public static final Comparator<MappingEntry> MAPPING_COMPARATOR_BY_GENERATED_POSITION = new Comparator<MappingEntry>() {
    @Override
    public int compare(MappingEntry o1, MappingEntry o2) {
      if (o1.getGeneratedLine() == o2.getGeneratedLine()) {
        return o1.getGeneratedColumn() - o2.getGeneratedColumn();
      }
      else {
        return o1.getGeneratedLine() - o2.getGeneratedLine();
      }
    }
  };

  public static SourceMap decode(@NotNull String contents, @NotNull Function<List<String>, SourceResolver> sourceResolverFactory) throws IOException {
    if (contents.isEmpty()) {
      throw new IOException("source map contents cannot be empty");
    }

    CharSequence in = contents;
    if (contents.startsWith(")]}")) {
      in = new CharSequenceSubSequence(contents, contents.indexOf('\n') + 1, contents.length());
    }
    return decode(in, sourceResolverFactory);
  }

  @Nullable
  public static SourceMap decode(@NotNull CharSequence in, @NotNull Function<List<String>, SourceResolver> sourceResolverFactory) throws IOException {
    JsonReaderEx reader = new JsonReaderEx(in);
    List<MappingEntry> mappings = new ArrayList<MappingEntry>();
    return parseMap(reader, 0, 0, mappings, sourceResolverFactory);
  }

  @Nullable
  private static SourceMap parseMap(JsonReaderEx reader,
                                    int line,
                                    int column,
                                    List<MappingEntry> mappings,
                                    @NotNull Function<List<String>, SourceResolver> sourceResolverFactory) throws IOException {
    reader.beginObject();
    String sourceRoot = null;
    JsonReaderEx sourcesReader = null;
    List<String> names = null;
    String encodedMappings = null;
    String file = null;
    int version = -1;
    while (reader.hasNext()) {
      String propertyName = reader.nextName();
      if (propertyName.equals("sections")) {
        throw new IOException("sections is not supported yet");
      }
      else if (propertyName.equals("version")) {
        version = reader.nextInt();
      }
      else if (propertyName.equals("sourceRoot")) {
        sourceRoot = readSourcePath(reader);
      }
      else if (propertyName.equals("sources")) {
        sourcesReader = reader.subReader();
        reader.skipValue();
      }
      else if (propertyName.equals("names")) {
        reader.beginArray();
        if (reader.hasNext()) {
          names = new ArrayList<String>();
          do {
            names.add(reader.nextString(true));
          }
          while (reader.hasNext());
        }
        else {
          names = Collections.emptyList();
        }
        reader.endArray();
      }
      else if (propertyName.equals("mappings")) {
        encodedMappings = reader.nextString();
      }
      else if (propertyName.equals("file")) {
        file = reader.nextString();
      }
      else {
        // skip file or extensions
        reader.skipValue();
      }
    }
    reader.close();

    // check it before other checks, probably it is not sourcemap file
    if (StringUtil.isEmpty(encodedMappings)) {
      // empty map
      return null;
    }

    if (version != 3) {
      throw new IOException("Unsupported sourcemap version: " + version);
    }

    if (sourcesReader == null) {
      throw new IOException("sources is not specified");
    }

    List<String> sources = readSources(sourcesReader, sourceRoot);
    if (sources.isEmpty()) {
      // empty map, meteor can report such ugly maps
      return null;
    }

    @SuppressWarnings("unchecked")
    List<MappingEntry>[] reverseMappingsBySourceUrl = new List[sources.size()];
    readMappings(encodedMappings, line, column, mappings, reverseMappingsBySourceUrl, names);

    MappingList[] sourceToEntries = new MappingList[reverseMappingsBySourceUrl.length];
    for (int i = 0; i < reverseMappingsBySourceUrl.length; i++) {
      List<MappingEntry> entries = reverseMappingsBySourceUrl[i];
      if (entries != null) {
        Collections.sort(entries, MAPPING_COMPARATOR_BY_SOURCE_POSITION);
        sourceToEntries[i] = new SourceMappingList(entries);
      }
    }
    return new SourceMap(file, new GeneratedMappingList(mappings), sourceToEntries, sourceResolverFactory.fun(sources));
  }

  @Nullable
  private static String readSourcePath(JsonReaderEx reader) {
    return PathUtil.toSystemIndependentName(StringUtil.nullize(reader.nextString().trim()));
  }

  private static void readMappings(@NotNull String value,
                                   int line,
                                   int column,
                                   @NotNull List<MappingEntry> mappings,
                                   @NotNull List<MappingEntry>[] reverseMappingsBySourceUrl,
                                   @Nullable List<String> names) {
    if (StringUtil.isEmpty(value)) {
      return;
    }

    CharSequenceIterator charIterator = new CharSequenceIterator(value);
    int sourceIndex = 0;
    List<MappingEntry> reverseMappings = getMapping(reverseMappingsBySourceUrl, sourceIndex);
    int sourceLine = 0;
    int sourceColumn = 0;
    int nameIndex = 0;
    while (charIterator.hasNext()) {
      if (charIterator.peek() == ',') {
        charIterator.next();
      }
      else {
        while (charIterator.peek() == ';') {
          line++;
          column = 0;
          charIterator.next();
          if (!charIterator.hasNext()) {
            return;
          }
        }
      }

      column += Base64VLQ.decode(charIterator);
      if (isSeparator(charIterator)) {
        mappings.add(new UnmappedEntry(line, column));
        continue;
      }

      int sourceIndexDelta = Base64VLQ.decode(charIterator);
      if (sourceIndexDelta != 0) {
        sourceIndex += sourceIndexDelta;
        reverseMappings = getMapping(reverseMappingsBySourceUrl, sourceIndex);
      }
      sourceLine += Base64VLQ.decode(charIterator);
      sourceColumn += Base64VLQ.decode(charIterator);

      MappingEntry entry;
      if (isSeparator(charIterator)) {
        entry = new UnnamedEntry(line, column, sourceIndex, sourceLine, sourceColumn);
      }
      else {
        nameIndex += Base64VLQ.decode(charIterator);
        assert names != null;
        entry = new NamedEntry(names.get(nameIndex), line, column, sourceIndex, sourceLine, sourceColumn);
      }
      reverseMappings.add(entry);
      mappings.add(entry);
    }
  }

  private static List<String> readSources(@NotNull JsonReaderEx reader, @Nullable String sourceRootUrl) {
    reader.beginArray();
    List<String> sources;
    if (reader.peek() == JsonToken.END_ARRAY) {
      sources = Collections.emptyList();
    }
    else {
      sources = new SmartList<String>();
      do {
        String sourceUrl = readSourcePath(reader);
        sourceUrl = StringUtil.isEmpty(sourceRootUrl) ? sourceUrl : (sourceRootUrl + '/' + sourceUrl);
        sources.add(sourceUrl);
      }
      while (reader.hasNext());
    }
    reader.endArray();
    return sources;
  }

  private static List<MappingEntry> getMapping(@NotNull List<MappingEntry>[] reverseMappingsBySourceUrl, int sourceIndex) {
    List<MappingEntry> reverseMappings = reverseMappingsBySourceUrl[sourceIndex];
    if (reverseMappings == null) {
      reverseMappings = new ArrayList<MappingEntry>();
      reverseMappingsBySourceUrl[sourceIndex] = reverseMappings;
    }
    return reverseMappings;
  }

  private static boolean isSeparator(CharSequenceIterator charIterator) {
    if (!charIterator.hasNext()) {
      return true;
    }

    char current = charIterator.peek();
    return current == ',' || current == ';';
  }

  /**
   * Not mapped to a section in the original source.
   */
  private static class UnmappedEntry extends MappingEntry {
    private final int line;
    private final int column;

    UnmappedEntry(int line, int column) {
      this.line = line;
      this.column = column;
    }

    @Override
    public int getGeneratedColumn() {
      return column;
    }

    @Override
    public int getGeneratedLine() {
      return line;
    }

    @Override
    public int getSourceLine() {
      return UNMAPPED;
    }

    @Override
    public int getSourceColumn() {
      return UNMAPPED;
    }
  }

  /**
   * Mapped to a section in the original source.
   */
  private static class UnnamedEntry extends UnmappedEntry {
    private final int source;
    private final int sourceLine;
    private final int sourceColumn;

    UnnamedEntry(int line, int column, int source, int sourceLine, int sourceColumn) {
      super(line, column);

      this.source = source;
      this.sourceLine = sourceLine;
      this.sourceColumn = sourceColumn;
    }

    @Override
    public int getSource() {
      return source;
    }

    @Override
    public int getSourceLine() {
      return sourceLine;
    }

    @Override
    public int getSourceColumn() {
      return sourceColumn;
    }
  }

  /**
   * Mapped to a section in the original source, and is associated with a name.
   */
  private static class NamedEntry extends UnnamedEntry {
    private final String name;

    NamedEntry(String name, int line, int column, int source, int sourceLine, int sourceColumn) {
      super(line, column, source, sourceLine, sourceColumn);
      this.name = name;
    }

    @Override
    public String getName() {
      return name;
    }
  }

  // java CharacterIterator is ugly, next() impl, so, we reinvent
  private static class CharSequenceIterator implements CharIterator {
    private final CharSequence content;
    private final int length;
    private int current = 0;

    CharSequenceIterator(CharSequence content) {
      this.content = content;
      length = content.length();
    }

    @Override
    public char next() {
      return content.charAt(current++);
    }

    char peek() {
      return content.charAt(current);
    }

    @Override
    public boolean hasNext() {
      return current < length;
    }
  }

  private static final class SourceMappingList extends MappingList {
    public SourceMappingList(@NotNull List<MappingEntry> mappings) {
      super(mappings);
    }

    @Override
    public int getLine(@NotNull MappingEntry mapping) {
      return mapping.getSourceLine();
    }

    @Override
    public int getColumn(@NotNull MappingEntry mapping) {
      return mapping.getSourceColumn();
    }

    @Override
    protected Comparator<MappingEntry> getComparator() {
      return MAPPING_COMPARATOR_BY_SOURCE_POSITION;
    }
  }

  private static final class GeneratedMappingList extends MappingList {
    public GeneratedMappingList(List<MappingEntry> mappings) {
      super(mappings);
    }

    @Override
    public int getLine(@NotNull MappingEntry mapping) {
      return mapping.getGeneratedLine();
    }

    @Override
    public int getColumn(@NotNull MappingEntry mapping) {
      return mapping.getGeneratedColumn();
    }

    @Override
    protected Comparator<MappingEntry> getComparator() {
      return MAPPING_COMPARATOR_BY_GENERATED_POSITION;
    }
  }
}