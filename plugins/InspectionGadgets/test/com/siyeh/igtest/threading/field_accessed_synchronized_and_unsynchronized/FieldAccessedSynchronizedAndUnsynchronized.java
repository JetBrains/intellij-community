package com.siyeh.igtest.threading.field_accessed_synchronized_and_unsynchronized;

public class FieldAccessedSynchronizedAndUnsynchronized
{
    private final Object m_lock = new Object();          
    private Object <warning descr="Field 'm_contents' is accessed in both synchronized and unsynchronized contexts">m_contents</warning> = new Object();
    private Object <warning descr="Field 'a' is accessed in both synchronized and unsynchronized contexts">a</warning>;
    private Object b;

    public void foo()
    {
        synchronized(m_lock)
        {
            m_contents = new Object();
            a = new Object();
            b = new Object();
        }
        getContents();
    }

    private Object getContents()
    {
        getContents2();
        return m_contents;
    }

    private void getContents2() {
        getContents();
    }

    public synchronized void g() {
        Runnable r = () -> {
            System.out.println(a);
        };
    }

    public void h() {
        assert Thread.holdsLock(m_lock);
        System.out.println(b);
    }

}
class Test {
  private Object <warning descr="Field 'object' is accessed in both synchronized and unsynchronized contexts">object</warning>;

  synchronized Runnable method() {
    return new Runnable() {
      @Override public void run() {
        System.out.println(object);
      }
    };
  }

  synchronized void setObject(Object object) {
    this.object = object;
  }
}
class AAAAAAA {

  private boolean ready;
  private final Object LOCK = new Object();

  void h(int i) {
    synchronized (LOCK) {
      ready = true;
    }
    new Runnable () {
      @Override
      public void run() {
        synchronized (LOCK) {
          ready = false;
        }
      }
    };
  }
}

class LambdaInsideConstructorOrInitializer {
  private String <warning descr="Field 'myFoo' is accessed in both synchronized and unsynchronized contexts">myFoo</warning>;
  private String <warning descr="Field 'myBar' is accessed in both synchronized and unsynchronized contexts">myBar</warning>;
  {
    myBar = "";
    Runnable r = new Runnable() {
      @Override
      public void run() {
        System.out.println(myBar);
      }
    };
  }

  public LambdaInsideConstructorOrInitializer() {
    myFoo = "";
    Runnable r = new Runnable() {
      @Override
      public void run() {
        System.out.println(myFoo);
      }
    };
  }
  
  synchronized void sout() {
    System.out.println(myFoo);
    System.out.println(myBar);
  }
}
class EditorView {

  private String a = "";

  public void one() {
    a = "beard";
  }

  private void two() {
    // private method always called while synchronized
  }

  String three() {
    synchronized (this) {
      two();
    }
    return a;
  }
}