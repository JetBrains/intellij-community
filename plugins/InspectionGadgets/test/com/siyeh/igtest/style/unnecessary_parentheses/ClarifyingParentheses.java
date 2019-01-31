package com.siyeh.igtest.style.clarifying_parentheses;

class UnnecessaryParentheses {

  String oldValue;
  boolean update(UnnecessaryParentheses that) {
    final boolean c = ("" +  "asdf") instanceof String;
    boolean b = true && (that instanceof Object);
    if ((oldValue != null) ? !oldValue.equals(that.oldValue) : (that.oldValue != null)) {
      return false;
    }
    return true;
  }
}
class ConditionalCondition {

  String s;
  String t;

  public boolean equals(Object other) {
    if (!(other instanceof ConditionalCondition)) return false;
    final ConditionalCondition condition = (ConditionalCondition)other;


    return (s != null ? !s.equals(condition.s) : condition.s != null) ? false : t.equals(condition.t);
  }
}