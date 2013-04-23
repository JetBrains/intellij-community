package p1.p2;

import java.lang.Error;
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
    n5 = R.styleable.MyStyleable_android_text;
    int n6 = R.plurals.plural1;
    n6 = R.plurals.<error>plural2</error>;
    int n7 = R.fraction.my_fraction1;
    n7 = R.fraction.my_fraction2;
    n7 = R.fraction.<error>my_fraction3</error>;

    switch(n1) {
      case R.string.my_string:
        System.out.println("abacaba");
        break;
      case R.string.my_string1:
        System.out.println("abacaba1");
        break;
      case R.attr.myAttr1:
        System.out.println("abacaba1");
        break;
      default:
        break;
    }
  }
}