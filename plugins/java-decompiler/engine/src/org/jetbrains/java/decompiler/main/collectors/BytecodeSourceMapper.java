package org.jetbrains.java.decompiler.main.collectors;

import java.util.*;
import java.util.Map.Entry;

import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.TextBuffer;

public class BytecodeSourceMapper {

  private int offset_total;

  private final HashMap<Integer, Integer> myOriginalLinesMapping = new HashMap<Integer, Integer>();

  // class, method, bytecode offset, source line
  private final HashMap<String, HashMap<String, HashMap<Integer, Integer>>> mapping = new LinkedHashMap<String, HashMap<String, HashMap<Integer, Integer>>>(); // need to preserve order

  public void addMapping(String classname, String methodname, int bytecode_offset, int source_line) {

    HashMap<String, HashMap<Integer, Integer>> class_mapping = mapping.get(classname);
    if(class_mapping == null) {
      mapping.put(classname, class_mapping = new LinkedHashMap<String, HashMap<Integer, Integer>>()); // need to preserve order
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

  public void addTracer(String classname, String methodname, BytecodeMappingTracer tracer) {
    for(Entry<Integer, Integer> entry : tracer.getMapping().entrySet()) {
      addMapping(classname, methodname, entry.getKey(), entry.getValue());
    }
    myOriginalLinesMapping.putAll(tracer.getOriginalLinesMapping());
  }

  public void dumpMapping(TextBuffer buffer, boolean offsetsToHex) {

    String lineSeparator = DecompilerContext.getNewLineSeparator();

    for(Entry<String, HashMap<String, HashMap<Integer, Integer>>> class_entry : mapping.entrySet()) {
      HashMap<String, HashMap<Integer, Integer>> class_mapping = class_entry.getValue();
      buffer.append("class " + class_entry.getKey() + "{" + lineSeparator);

      boolean is_first_method = true;

      for(Entry<String, HashMap<Integer, Integer>> method_entry : class_mapping.entrySet()) {
        HashMap<Integer, Integer> method_mapping = method_entry.getValue();

        if(!is_first_method) {
          buffer.appendLineSeparator();
        }
        buffer.appendIndent(1).append("method " + method_entry.getKey() + "{" + lineSeparator);

        List<Integer> lstBytecodeOffsets = new ArrayList<Integer>(method_mapping.keySet());
        Collections.sort(lstBytecodeOffsets);

        for(Integer offset : lstBytecodeOffsets) {
          Integer line = method_mapping.get(offset);

          String strOffset = offsetsToHex ? Integer.toHexString(offset): line.toString();
          buffer.appendIndent(2).append(strOffset).appendIndent(2).append((line + offset_total) + lineSeparator);
        }
        buffer.appendIndent(1).append("}").appendLineSeparator();
        is_first_method = false;
      }
      buffer.append("}").appendLineSeparator();
    }

    // lines mapping
    buffer.append("Lines mapping:").appendLineSeparator();
    int[] mapping = getOriginalLinesMapping();
    for (int i = 0; i < mapping.length; i+=2) {
      buffer.append(mapping[i]).append(" <-> ").append(mapping[i+1]).appendLineSeparator();
    }
  }

  public int getTotalOffset() {
    return offset_total;
  }

  public void setTotalOffset(int offset_total) {
    this.offset_total = offset_total;
  }

  public void addTotalOffset(int offset_total) {
    this.offset_total += offset_total;
  }

  /**
   * original to our line mapping
   */
  public int[] getOriginalLinesMapping() {
    int[] res = new int[myOriginalLinesMapping.size()*2];
    int i = 0;
    for (Entry<Integer, Integer> entry : myOriginalLinesMapping.entrySet()) {
      res[i] = entry.getKey();
      res[i+1] = entry.getValue() + offset_total + 1; // make it 1 based
      i+=2;
    }
    return res;
  }
}
