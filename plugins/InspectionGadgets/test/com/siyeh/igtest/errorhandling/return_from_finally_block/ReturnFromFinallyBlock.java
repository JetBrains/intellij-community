package com.siyeh.igtest.errorhandling.return_from_finally_block;

import java.util.concurrent.Callable;

public class ReturnFromFinallyBlock
{
    public void foo()
    {
        try
        {

        }
        finally
        {
            return;
        }
    }

    public int bar()
    {
        try
        {
           return 4;
        }
        finally
        {
            return 3;
        }
    }

  public void test() {
    try {
    }
    finally {
      try {
        new Callable<Object>() {
          @Override
          public Object call() throws Exception {
            return null;
          }
        };
      }
      catch (Exception e) {
      }
    }
  }
}
