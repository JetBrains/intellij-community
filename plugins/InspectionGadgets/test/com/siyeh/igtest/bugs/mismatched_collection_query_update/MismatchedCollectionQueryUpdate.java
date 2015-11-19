package com.siyeh.igtest.bugs.mismatched_collection_query_update;

import java.util.*;
import java.io.FileInputStream;
import java.util.concurrent.BlockingQueue;

public class MismatchedCollectionQueryUpdate {
    private Set foo = new HashSet();
    private Set <warning descr="Contents of collection 'foo2' are queried, but never updated">foo2</warning> = new HashSet();
    private Set <warning descr="Contents of collection 'bar' are queried, but never updated">bar</warning> ;
    private Set bar2 = new HashSet(foo2);
    private Set bal ;

    public void bar()
    {
        foo.add(new Integer(bar.size()));
        final boolean found = foo.add(new Integer(bar2.size()));
    }

    public void bar2()
    {
        final List <warning descr="Contents of collection 'barzoom' are updated, but never queried">barzoom</warning> = new ArrayList(3);
        barzoom.add(new Integer(3));
    }

    public List bar4()
    {
        final List barzoom = new ArrayList(3);
        barzoom.add(new Integer(3));
        return true?null:barzoom;
    }

    public List bar3()
    {
        final List barzoom = new ArrayList(3);
        barzoom.add(new Integer(3));
        return barzoom;
    }

    class Node{
        private SortedSet mChildren;

        public synchronized SortedSet getChildren(){
            if(mChildren == null){
                mChildren = queryChildren();
            }
            return mChildren;
        }

        private SortedSet queryChildren(){
            return null;
        }
    }
    static class SNMP {
        public static final int AGENT_PORT;
        public static final int MANAGER_PORT;

        static {
            int agentPort;
            int mgrPort;
            try {
                Properties p = new Properties();
                p.loadFromXML(new FileInputStream("config/comms.xml"));
                agentPort = Integer.parseInt(p.getProperty("snmp.agent.port"));
                mgrPort = Integer.parseInt(p.getProperty("snmp.manager.port"));
            } catch (Exception e) {
                agentPort = 161;
                mgrPort = 162;

            }
            AGENT_PORT = agentPort;
            MANAGER_PORT = mgrPort;
        }
    }

    class A {
        private final List<String> list = new ArrayList();

        void foo(String s) {
            list.add(s);
        }

        boolean bar(String s, boolean b, Set<String> set2) {
            return (b ? list : set2).contains(s);
        }
    }

    class B {
        private final List<String> list = new ArrayList();

        boolean foo(String s) {
            return list.contains(s);
        }

        void bar(String s, boolean b, Set<String> set2) {
            (b ? list : set2).add(s);
        }
    }

    class C {
        private final java.util.concurrent.BlockingDeque deque =
        new java.util.concurrent.LinkedBlockingDeque();

        {
            try {
                final Object o = deque.takeFirst();
                deque.putLast(o);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

        }
    }

    class Test {
        public String add(final String value) {
            final List<String> list = getList();
            list.add(value);
            return value;
        }

        private List<String> getList() {
            return null;
        }
    }

  private static String foos() {
    final List bar = new ArrayList();
    bar.add("okay");
    return "not " + bar + "";
  }

  void methodArgument() {
    List<String> <warning descr="Contents of collection 'foos' are updated, but never queried">foos</warning> = new ArrayList<>();
    List<String> <warning descr="Contents of collection 'bars' are queried, but never updated">bars</warning> = new ArrayList<>();

    foos.removeAll( bars );

    List<String> other = new ArrayList<>();
    other.add("a");
    m(other);
  }

  void m(List l) {}

  public void foo()
  {
    final Set localFoo = foo;
  }

  public void foofoo()
  {
    final Map<String, String> <warning descr="Contents of collection 'anotherMap' are queried, but never updated">anotherMap</warning> = new HashMap<String, String>();
    final SortedMap<String, String> map = new TreeMap<String, String>(anotherMap);
    final Iterator<String> it = map.keySet().iterator();
    while(it.hasNext()){
      Object o = it.next();

    }
  }

  class MyClass {
    private  List<String> ourValues = new ArrayList<String>() {{
      Collections.addAll((this), "A", "B", "C");
    }};

    public void foo() {
      for (final String value : ourValues) {}
    }
  }

  class MyClass2 {
    private  List<String> ourValues = new ArrayList<String>() {{
      (this).add("");
    }};

    public void foo() {
      for (final String value : ourValues) {}
    }
  }

  private void updateAttachmentWarning(final String message) {
    final List<String> includedAttachments;
    if (message instanceof Object &&
        !(includedAttachments = boo()).isEmpty()) {
      if (includedAttachments.size() == 1) {
      }
    }
  }

  List<String> boo() {return null;}

  private List<? extends CharSequence> sequences = null;

  {
    sequences.stream().map(CharSequence ::length);
  }
}

class MethReference<E> {
    private String foo(){
        List<E> list = new ArrayList<>();
        forEach(list::add);
        return list.toString();
    }

  void copyStuff(List other) {
    List <warning descr="Contents of collection 'list' are updated, but never queried">list</warning> = new ArrayList();
    J j = list::add;
  }

    private void forEach(I<E> ei) {}
    interface I<E> {
        boolean _(E e);
    }
    interface J<E> {
      void m(E e);
    }

    void qTest() {
        Map<Integer, Boolean> map = new HashMap<>();
        map.put(1, true);
        I<Integer> mapper = map::get;
    }

  void foo(int a,Collection b) {
    final ArrayList x = new ArrayList();
    x.add("1");

    for (Object o : a>0? b : x)
    {
      System.out.println(o);
    }
  }
}
class CollectionsUser {
  void a(List<String> list) {
    List<String> l = new ArrayList();
    Collections.addAll(l, "1", "2", "4");
    Collections.copy(list, l);
  }

  int b(List<String> list) {
    List<String> l = new ArrayList();
    Collections.addAll(l, "1", "2", "4");
    return Collections.indexOfSubList(list, l);
  }

  void c() {
    List<String> <warning descr="Contents of collection 'l' are queried, but never updated">l</warning> = new ArrayList();
    final int frequency = Collections.frequency(l, "one");
  }

  List<String> d() {
    List<String> <warning descr="Contents of collection 'l' are queried, but never updated">l</warning> = new ArrayList();
    return Collections.unmodifiableList(l);
  }

  List<String> e() {
    List<String> l = new ArrayList<String>();
    return Collections.checkedList(l, String.class);
  }

  private final List<Object> <warning descr="Contents of collection 'list' are updated, but never queried">list</warning> = new ArrayList<Object>();
  public void add() {
    Collections.addAll(list, "Hello");
  }

  Supplier<List<String>> i() {
    final List<String> bas = new ArrayList<>();
    bas.add("asdf");
    return () -> bas;
  }

  Supplier<List<String>> j() {
    final List<String> bas = new ArrayList<>();
    bas.add("asdf");
    return () -> {return bas;};
  }

  interface Supplier<T> {
    T get();
  }

  void draining(BlockingQueue<Object> queue) {
    List<Object> objects = new ArrayList<>();
    queue.drainTo(objects);
    // ...
    for (Object obj : objects) {
      //  ...
    }
  }

  void merging() {
    Map<String, String> map = new HashMap<>();
    map.merge("key", "value", (i,j)->j);
    map.forEach((k,v) -> System.out.println(k + " : " + v));
  }
}

class SimpleAdd {
  protected String[] doPerform() {
    List<String> result = new ArrayList<String>();
    for (String app : getApplications()) {
      if (app.startsWith("")) {
        result.add(app);
      }
    }
    return result.toArray(new String[result.size()]);
  }

  public String[] getApplications() {
    return null;
  }

}
class EnumConstant {
  private static final List<String> CONSTANT_ARRAY = new ArrayList();
  static {
    CONSTANT_ARRAY.add("asdf");
  }

  enum SomeEnum {
    ITEM(CONSTANT_ARRAY); // passed as argument
    private final List<String> myPatterns;
    SomeEnum(List<String> patterns) {
      myPatterns = patterns;
    }
  }
}
class ToArray {{
  List list = new ArrayList();
  list.add("A thing");
  list.toArray(new Object[1]);
}}