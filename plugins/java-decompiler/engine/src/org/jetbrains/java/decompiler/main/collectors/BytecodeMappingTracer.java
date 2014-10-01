package org.jetbrains.java.decompiler.main.collectors;

import java.util.HashMap;

public class BytecodeMappingTracer {

  private int current_sourceline;
  
  // bytecode offset, source line
  private HashMap<Integer, Integer> mapping;
  
  public void incrementSourceLine() {
    current_sourceline++;
  }
  
  public void addMapping(int bytecode_offset) {
    if(!mapping.containsKey(bytecode_offset)) {
      mapping.put(bytecode_offset, current_sourceline);
    }
  }

  public HashMap<Integer, Integer> getMapping() {
    return mapping;
  }  
  
}
