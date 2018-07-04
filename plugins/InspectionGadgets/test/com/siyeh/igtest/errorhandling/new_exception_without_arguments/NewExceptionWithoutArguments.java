package com.siyeh.igtest.errorhandling.new_exception_without_arguments;

class NewExceptionWithoutArguments {

  void foo() {
    throw new <warning descr="'new RuntimeException()' without arguments">RuntimeException</warning>();
  }

  void bar() {
    throw new MyException();
  }

}
class MyException extends RuntimeException {

}