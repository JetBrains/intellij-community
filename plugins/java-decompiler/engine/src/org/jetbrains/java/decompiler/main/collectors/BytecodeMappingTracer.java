package org.jetbrains.java.decompiler.main.collectors;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

public class BytecodeMappingTracer {

  private int current_sourceline;

  // bytecode offset, source line
  private HashMap<Integer, Integer> mapping = new HashMap<Integer, Integer>();

  public BytecodeMappingTracer() {}

  public BytecodeMappingTracer(int initial_source_line) {
    current_sourceline = initial_source_line;
  }

  public void incrementCurrentSourceLine() {
    current_sourceline++;
  }

  public void incrementCurrentSourceLine(int number_lines) {
    current_sourceline += number_lines;
  }

  public void shiftSourceLines(int shift) {
    for(Entry<Integer, Integer> entry : mapping.entrySet()) {
      entry.setValue(entry.getValue() + shift);
    }
  }

  public void addMapping(int bytecode_offset) {
    if(!mapping.containsKey(bytecode_offset)) {
      mapping.put(bytecode_offset, current_sourceline);
    }
  }

  public void addMapping(Set<Integer> bytecode_offsets) {
    if(bytecode_offsets != null) {
      for(Integer bytecode_offset : bytecode_offsets) {
        addMapping(bytecode_offset);
      }
    }
  }

  public void addTracer(BytecodeMappingTracer tracer) {
    if(tracer != null) {
      for(Entry<Integer, Integer> entry : tracer.mapping.entrySet()) {
        if(!mapping.containsKey(entry.getKey())) {
          mapping.put(entry.getKey(), entry.getValue());
        }
      }
    }
  }

  public HashMap<Integer, Integer> getMapping() {
    return mapping;
  }

  public int getCurrentSourceLine() {
    return current_sourceline;
  }

  public void setCurrentSourceLine(int current_sourceline) {
    this.current_sourceline = current_sourceline;
  }

}
