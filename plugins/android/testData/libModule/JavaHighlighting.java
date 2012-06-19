package p1.p2.lib;

import java.lang.System;

public class JavaHighlighting {
  public void f(int n) {
    int m = R.string.myLibResource;
    switch(n) {
      case <error>R.string.myLibResource</error>:
        System.out.println("aba");
        break;
      default:
        break;
    }
  }
}