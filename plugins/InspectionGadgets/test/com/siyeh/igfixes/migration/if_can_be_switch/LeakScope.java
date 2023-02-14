package com.siyeh.ipp.switchtoif.replace_if_with_switch;

public class LeakScope {

  private void leakDeclarationScope(Object o) {
    <caret>if (o instanceof String text) {
      //do
    } else {
      return;
    }
    System.out.println(text);
}