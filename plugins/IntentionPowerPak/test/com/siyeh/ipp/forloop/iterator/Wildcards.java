package com.siyeh.ipp.forloop.iterator;

import java.util.List;
import java.util.Map;

class Wildcards {

  void renames(Map<? extends List, String> allRenames) {
    for<caret> (Map.Entry<? extends List, String> entry : allRenames.entrySet()) {
    }
  }
}