package p1.p2;

import java.lang.Readable;
import java.lang.System;

public class JavaCompletion1 {
  public void f() {
    int n1 = R.string.my_string;
    int n2 = R.string.<error>unknown</error>;
    <error>int n3 = R.styleable.MyStyleable;</error>
    int[] n4 = R.styleable.MyStyleable;
    int n5 = R.attr.myAttr1;
    n5 = R.attr.myAttr2;
    n5 = R.attr.<error>android_text</error>;
    n5 = R.styleable.MyStyleable_myAttr1;
    <error>n4 = R.styleable.MyStyleable_myAttr1</error>;
    n5 = R.styleable.<error>MyStyleable_text</error>;

    switch(n1) {
      case R.string.my_string:
        System.out.println("abacaba");
        break;
      default:
        break;
    }
  }
}