package com.siyeh.igfixes.style.replace_with_string;

class Escape {
  {
    String s = (((((new <caret>StringBuilder()).append('"')).append(0).append("bas"))).toString());
  }
}