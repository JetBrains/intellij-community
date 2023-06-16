// "Replace with 'isEmpty()'" "true"
package com.siyeh.igtest.style.size_replaceable_by_is_empty;

import java.util.LinkedList;

public class SizeReplaceableByIsEmpty {
  boolean equalsToZero(LinkedList l) {
    return l.<caret>size() < 1;
  }
}
