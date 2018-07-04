package com.siyeh.ipp.conditional.withIf;

class ConditionalInIf {
  private Object value;

  public boolean equals(ConditionalInIf that) {
    if (value != null ? <caret>!value.equals(that.value) : that.value != null) {
      return false;
    }

    return true;
  }
}