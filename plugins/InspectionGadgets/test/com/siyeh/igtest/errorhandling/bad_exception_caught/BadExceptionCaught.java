package com.siyeh.igtest.errorhandling.bad_exception_caught;



class BadExceptionCaught {

  public void test() {
    try
    {
      // some code here
    }
    catch(NullPointerException  | UnsupportedOperationException e)
    {
      throw e;
    }
    catch(Exception e)
    {
      throw new RuntimeException(e);
    }
  }
}