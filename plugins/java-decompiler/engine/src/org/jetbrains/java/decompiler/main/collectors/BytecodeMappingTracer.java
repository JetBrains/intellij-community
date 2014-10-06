package org.jetbrains.java.decompiler.main.collectors;

import java.util.HashMap;
import java.util.Set;

public class BytecodeMappingTracer {

  private int current_sourceline;

  // bytecode offset, source line
  private HashMap<Integer, Integer> mapping = new HashMap<Integer, Integer>();

  public BytecodeMappingTracer() {}

  public BytecodeMappingTracer(int initial_source_line) {
    current_sourceline = initial_source_line;
  }

  public void incrementSourceLine() {
    current_sourceline++;
  }

  public void incrementSourceLine(int number_lines) {
    current_sourceline += number_lines;
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

  public HashMap<Integer, Integer> getMapping() {
    return mapping;
  }

  public int getCurrentSourceline() {
    return current_sourceline;
  }

  public void setCurrentSourceline(int current_sourceline) {
    this.current_sourceline = current_sourceline;
  }

}
