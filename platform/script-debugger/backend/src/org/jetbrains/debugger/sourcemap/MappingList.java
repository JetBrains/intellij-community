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

import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public abstract class MappingList {
  private final List<MappingEntry> mappings;

  public MappingList(@NotNull List<MappingEntry> mappings) {
    this.mappings = mappings;
  }

  public int getSize() {
    return mappings.size();
  }

  protected abstract Comparator<MappingEntry> getComparator();

  public abstract int getLine(@NotNull MappingEntry mapping);

  public abstract int getColumn(@NotNull MappingEntry mapping);

  public int indexOf(int line, int column) {
    int low = 0;
    int high = mappings.size() - 1;
    if (getLine(mappings.get(low)) > line || getLine(mappings.get(high)) < line) {
      return -1;
    }

    while (low <= high) {
      int middle = (low + high) >>> 1;
      MappingEntry mapping = mappings.get(middle);
      int mappingLine = getLine(mapping);
      if (line == mappingLine) {
        if (column == getColumn(mapping)) {
          // find first
          int firstIndex = middle;
          while (firstIndex > 0) {
            MappingEntry prevMapping = mappings.get(firstIndex - 1);
            if (getLine(prevMapping) == line && getColumn(prevMapping) == column) {
              firstIndex--;
            }
            else {
              break;
            }
          }
          return firstIndex;
        }
        else if (column < getColumn(mapping)) {
          if (column == 0 || column == -1) {
            // find first
            int firstIndex = middle;
            while (firstIndex > 0 && getLine(mappings.get(firstIndex - 1)) == line) {
              firstIndex--;
            }
            return firstIndex;
          }

          if (middle == 0) {
            return -1;
          }

          MappingEntry prevMapping = mappings.get(middle - 1);
          if (line != getLine(prevMapping)) {
            return -1;
          }
          else if (column >= getColumn(prevMapping)) {
            return middle - 1;
          }
          else {
            high = middle - 1;
          }
        }
        else {
          // https://code.google.com/p/google-web-toolkit/issues/detail?id=9103
          // We skipIfColumnEquals because GWT has two entries — source position equals, but generated no. We must use first entry (at least, in case of GWT it is correct)
          MappingEntry nextMapping = getNextOnTheSameLine(middle);
          if (nextMapping == null) {
            return middle;
          }
          else {
            low = middle + 1;
          }
        }
      }
      else if (line > mappingLine) {
        low = middle + 1;
      }
      else {
        high = middle - 1;
      }
    }

    return -1;
  }

  @Nullable
  public MappingEntry get(int line, int column) {
    // todo honor Google Chrome bug related to paused location
    int index = indexOf(line, column);
    return index == -1 ? null : mappings.get(index);
  }

  @Nullable
  public MappingEntry getNext(int index) {
    return index >= 0 && (index + 1) < mappings.size() ? mappings.get(index + 1) : null;
  }

  @Nullable
  public MappingEntry getNext(@NotNull MappingEntry mapping) {
    return getNext(Collections.binarySearch(mappings, mapping, getComparator()));
  }

  @Nullable
  public MappingEntry getNextOnTheSameLine(int index) {
    return getNextOnTheSameLine(index, true);
  }

  @Nullable
  public MappingEntry getNextOnTheSameLine(int index, boolean skipIfColumnEquals) {
    MappingEntry nextMapping = getNext(index);
    if (nextMapping == null) {
      return null;
    }

    MappingEntry mapping = get(index);
    if (getLine(nextMapping) != getLine(mapping)) {
      return null;
    }

    if (skipIfColumnEquals) {
      // several generated segments can point to one source segment, so, in mapping list ordered by source, could be several mappings equal in terms of source position
      while (nextMapping != null && getColumn(nextMapping) == getColumn(mapping)) {
        nextMapping = getNextOnTheSameLine(++index, false);
      }
    }

    return nextMapping;
  }

  @SuppressWarnings("UnusedDeclaration")
  @Nullable
  public MappingEntry getNextOnTheSameLine(@NotNull MappingEntry mapping) {
    MappingEntry nextMapping = getNext(mapping);
    return nextMapping != null && getLine(nextMapping) == getLine(mapping) ? nextMapping : null;
  }

  public int getEndOffset(@NotNull MappingEntry mapping, int lineStartOffset, @NotNull Document document) {
    MappingEntry nextMapping = getNextOnTheSameLine(Collections.binarySearch(mappings, mapping, getComparator()));
    return nextMapping == null ? document.getLineEndOffset(getLine(mapping)) : lineStartOffset + getColumn(nextMapping);
  }

  @NotNull
  public MappingEntry get(int index) {
    return mappings.get(index);
  }

  public interface MappingsProcessorInLine {
    boolean process(@NotNull MappingEntry entry, @Nullable MappingEntry nextEntry);
  }

  // entries will be processed in this list order
  public boolean processMappingsInLine(int line, @NotNull MappingsProcessorInLine entryProcessor) {
    int low = 0;
    int high = mappings.size() - 1;
    while (low <= high) {
      int middle = (low + high) >>> 1;
      MappingEntry mapping = mappings.get(middle);
      int mappingLine = getLine(mapping);
      if (line == mappingLine) {
        // find first
        int firstIndex = middle;
        while (firstIndex > 0 && getLine(mappings.get(firstIndex - 1)) == line) {
          firstIndex--;
        }

        MappingEntry entry = mappings.get(firstIndex);
        do {
          MappingEntry nextEntry = ++firstIndex < mappings.size() ? mappings.get(firstIndex) : null;
          if (nextEntry != null && getLine(nextEntry) != line) {
            nextEntry = null;
          }

          if (!entryProcessor.process(entry, nextEntry)) {
            return true;
          }

          entry = nextEntry;
        }
        while (entry != null);
        return true;
      }
      else if (line > mappingLine) {
        low = middle + 1;
      }
      else {
        high = middle - 1;
      }
    }
    return false;
  }
}
