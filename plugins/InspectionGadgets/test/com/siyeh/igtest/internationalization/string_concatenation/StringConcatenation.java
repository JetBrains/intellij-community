package com.siyeh.igtest.internationalization.string_concatenation;

import org.jetbrains.annotations.NonNls;

public class StringConcatenation
{
    public StringConcatenation()
    {
    }

    public void foo()
    {
        final String concat = "foo" + "bar";
        System.out.println("concat = " + concat);
        System.out.println("a" + "b" + "c");
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