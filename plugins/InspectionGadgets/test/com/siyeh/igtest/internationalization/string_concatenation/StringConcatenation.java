package com.siyeh.igtest.internationalization.string_concatenation;

import org.jetbrains.annotations.NonNls;

public class StringConcatenation
{
    public StringConcatenation()
    {
    }

    public void foo()
    {
        final String concat = "foo" <warning descr="String concatenation '+' in an internationalized context">+</warning> "bar";
        System.out.println("concat = " <warning descr="String concatenation '+' in an internationalized context">+</warning> concat);
        System.out.println("a" <warning descr="String concatenation '+' in an internationalized context">+</warning> "b" <warning descr="String concatenation '+' in an internationalized context">+</warning> "c");
    }

    public void boom() {
        @NonNls
        String string = "asdf" + " asdfasd";
        string += "asdfasd";
        boom("asdf" + "boom");
    }

    private void boom(@NonNls String s) {
    }
}

class ExceptionsInside {
  class MyException extends Exception {
      MyException(String message) {
          super(message);
      }
  }
  
  class MyChildException extends MyException {
      MyChildException(String a) {
          super("Message: " + a + "....");
      }
  }
}