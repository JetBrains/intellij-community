package org.jetbrains.java.decompiler.main.collectors;

import java.util.HashMap;
import java.util.Map.Entry;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

public class BytecodeSourceMapper {

  private int offset_total;
  
  // class, method, bytecode offset, source line
  private HashMap<String, HashMap<String, HashMap<Integer, Integer>>> mapping;
  
  public void addMapping(String classname, String methodname, int bytecode_offset, int source_line) {
    
    HashMap<String, HashMap<Integer, Integer>> class_mapping = mapping.get(classname);
    if(class_mapping == null) {
      mapping.put(classname, class_mapping = new HashMap<String, HashMap<Integer, Integer>>());
    }
    
    HashMap<Integer, Integer> method_mapping = class_mapping.get(methodname);
    if(method_mapping == null) {
      class_mapping.put(methodname, method_mapping = new HashMap<Integer, Integer>());
    }
    
    // don't overwrite
    if(!method_mapping.containsKey(bytecode_offset)) {
      method_mapping.put(bytecode_offset, source_line);
    }
  }

  public void dumpMapping(StringBuilder buffer) {
    
    String lineSeparator = DecompilerContext.getNewLineSeparator();
    String indentstr1 = InterpreterUtil.getIndentString(1);
    String indentstr2 = InterpreterUtil.getIndentString(2);

    
    for(Entry<String, HashMap<String, HashMap<Integer, Integer>>> class_entry : mapping.entrySet()) {
      HashMap<String, HashMap<Integer, Integer>> class_mapping = class_entry.getValue();
      buffer.append("class " + class_entry.getKey() + "{" + lineSeparator);
      
      boolean is_first_method = true;
      
      for(Entry<String, HashMap<Integer, Integer>> method_entry : class_mapping.entrySet()) {
        HashMap<Integer, Integer> method_mapping = method_entry.getValue();
        
        if(!is_first_method) {
          buffer.append(lineSeparator);
        }
        buffer.append(indentstr1 + "method " + method_entry.getKey() + "{" + lineSeparator);
        
        for(Entry<Integer, Integer> line : method_mapping.entrySet()) {
          buffer.append(indentstr2 + line.getKey() + indentstr2 + line.getValue() + lineSeparator);
        }
        buffer.append(indentstr1 + "}" + lineSeparator);
        is_first_method = false;
      }
      buffer.append("}" + lineSeparator);      
    }
  }
  
  public int getTotalOffset() {
    return offset_total;
  }

  public void setTotalOffset(int offset_total) {
    this.offset_total = offset_total;
  }
  
  
  
}
