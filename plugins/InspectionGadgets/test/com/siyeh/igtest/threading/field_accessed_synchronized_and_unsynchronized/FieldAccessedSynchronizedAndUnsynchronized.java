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