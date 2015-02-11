package com.siyeh.igtest.cloneable.clone_calls_super_clone;

public class CloneCallsSuperClone implements Cloneable
{
    
    public void foo()
    {
        
    }

    public Object <warning descr="'clone()' does not call 'super.clone()'">clone</warning>()
    {
        return this;
    }
}
class One {

  public final One clone() throws CloneNotSupportedException {
    throw new CloneNotSupportedException();
  }
}
final class Two {
  public Two clone() throws CloneNotSupportedException {
    throw (new CloneNotSupportedException());
  }
}
class Three {
  public Three <warning descr="'clone()' does not call 'super.clone()'">clone</warning>() throws CloneNotSupportedException {
    throw new CloneNotSupportedException();
  }
}
class Four {
  public final Four clone() {
    throw new UnsupportedOperationException();
  }
}
