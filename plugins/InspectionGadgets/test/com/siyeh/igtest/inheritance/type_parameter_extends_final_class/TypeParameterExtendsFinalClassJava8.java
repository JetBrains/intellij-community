package com.siyeh.igtest.inheritance.type_parameter_extends_final_class;

import java.util.*;


interface FunctionalInterface<T>{
  void test(List<? extends T> list, T item);
}

class TestFunctionalClass<T>{
  public void method(FunctionalInterface<? extends T> expectedLambda){
  }
}
public class TypeParameterExtendsFinalClassJava8{
  public void test(){
    new TestFunctionalClass<Integer>().method((List<? extends Integer> list, Integer item) -> {});
  }
}