package com.siyeh.igfixes.style.replace_with_string;

class Escape {
  {
    String s = new StringBuilder<caret>().append('\'').append("bas").toString();
  }
}