package com.siyeh.igtest.finalization;

public class FinalizeNotProtected
{
    public FinalizeNotProtected()
    {
    }

    public void <warning descr="'finalize()' should have protected access, not public">finalize</warning>() throws Throwable
    {
        super.finalize();
    }
}
class FinalizeProtected {

    protected void finalize() throws Throwable {
        super.finalize();
    }
}
interface Finalizer {

    void finalize();
}
class MyGraphics extends FinalizeNotProtected {

  public void finalize() throws Throwable { // no warning when overriding public method
    try {
      // cleanup
    } finally {
      super.finalize();
    }
  }
}