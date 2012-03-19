package com.siyeh.igtest.errorhandling.new_exception_without_arguments;

class NewExceptionWithoutArguments {

  void foo() {
    throw new RuntimeException();
  }

  void bar() {
    throw new MyException();
  }

}
class MyException extends RuntimeException {

}