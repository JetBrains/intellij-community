/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler.main.collectors;

import org.jetbrains.java.decompiler.struct.attr.StructLineNumberTableAttribute;

import java.util.*;
import java.util.Map.Entry;

public class BytecodeMappingTracer {
  public static final BytecodeMappingTracer DUMMY = new BytecodeMappingTracer();

  private int currentSourceLine;
  private StructLineNumberTableAttribute lineNumberTable = null;
  private final Map<Integer, Integer> mapping = new HashMap<>();  // bytecode offset, source line

  public BytecodeMappingTracer() { }

  public BytecodeMappingTracer(int initial_source_line) {
    currentSourceLine = initial_source_line;
  }

  public void incrementCurrentSourceLine() {
    currentSourceLine++;
  }

  public void incrementCurrentSourceLine(int number_lines) {
    currentSourceLine += number_lines;
  }

  public void addMapping(int bytecode_offset) {
    if (!mapping.containsKey(bytecode_offset)) {
      mapping.put(bytecode_offset, currentSourceLine);
    }
  }

  public void addMapping(Set<Integer> bytecode_offsets) {
    if (bytecode_offsets != null) {
      for (Integer bytecode_offset : bytecode_offsets) {
        addMapping(bytecode_offset);
      }
    }
  }

  public void addTracer(BytecodeMappingTracer tracer) {
    if (tracer != null) {
      for (Entry<Integer, Integer> entry : tracer.mapping.entrySet()) {
        if (!mapping.containsKey(entry.getKey())) {
          mapping.put(entry.getKey(), entry.getValue());
        }
      }
    }
  }

  public Map<Integer, Integer> getMapping() {
    return mapping;
  }

  public int getCurrentSourceLine() {
    return currentSourceLine;
  }

  public void setCurrentSourceLine(int currentSourceLine) {
    this.currentSourceLine = currentSourceLine;
  }

  public void setLineNumberTable(StructLineNumberTableAttribute lineNumberTable) {
    this.lineNumberTable = lineNumberTable;
  }

  private final Set<Integer> unmappedLines = new HashSet<>();

  public Set<Integer> getUnmappedLines() {
    return unmappedLines;
  }

  public Map<Integer, Integer> getOriginalLinesMapping() {
    if (lineNumberTable == null) {
      return Collections.emptyMap();
    }

    Map<Integer, Integer> res = new HashMap<>();

    // first match offsets from line number table
    int[] data = lineNumberTable.getRawData();
    for (int i = 0; i < data.length; i += 2) {
      int originalOffset = data[i];
      int originalLine = data[i + 1];
      Integer newLine = mapping.get(originalOffset);
      if (newLine != null) {
        res.put(originalLine, newLine);
      }
      else {
        unmappedLines.add(originalLine);
      }
    }

    // now match offsets from decompiler mapping
    for (Entry<Integer, Integer> entry : mapping.entrySet()) {
      int originalLine = lineNumberTable.findLineNumber(entry.getKey());
      if (originalLine > -1 && !res.containsKey(originalLine)) {
        res.put(originalLine, entry.getValue());
        unmappedLines.remove(originalLine);
      }
    }
    return res;
  }
}