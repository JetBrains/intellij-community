package com.siyeh.igtest.classlayout.emptyclass;

public class EmptyClass {
    {
      final java.util.ArrayList<String> stringList = new java.util.ArrayList<String>() {};
      System.out.println("");
    }
}
class MyList extends java.util.ArrayList<String> {}
class MyException extends java.lang.Exception {}
abstract class <warning descr="Class 'ReportMe' is empty">ReportMe</warning> implements java.util.List {}
