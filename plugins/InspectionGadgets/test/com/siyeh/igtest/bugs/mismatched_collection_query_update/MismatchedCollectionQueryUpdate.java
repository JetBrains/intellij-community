/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.igtest.bugs.mismatched_collection_query_update;

import java.util.*;
import java.util.stream.IntStream;
import java.io.FileInputStream;
import java.util.concurrent.BlockingQueue;

public class MismatchedCollectionQueryUpdate {
    private Set foo = new HashSet();
    private Set <warning descr="Contents of collection 'foo2' are queried, but never updated">foo2</warning> = new HashSet();
    private Set <warning descr="Contents of collection 'bar' are queried, but never updated">bar</warning> ;
    private Set bar2 = new HashSet(foo2);
    private Set bal ;
    private Set<String> injected = new HashSet<>();

    public void print() {
      for(String s : injected) {
        System.out.println(s);
      }
    }

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

    void testGetOrDefault(Map<String, Map<String, String>> otherMap, String otherKey, String key, String value) {
      // IDEA-185729
      final Map<String, String> map = otherMap.getOrDefault(otherKey, <warning descr="Contents of collection 'new HashMap<>()' are updated, but never queried">new HashMap<>()</warning>);
      map.put(key, value);
    }

    void testTernary(boolean b, List<String> orig) {
      List<String> list = b ? <warning descr="Contents of collection 'new ArrayList<>()' are updated, but never queried">new ArrayList<>()</warning> : orig;
      list.add("foo");
    }

    void testTernaryBothEmpty(boolean preserveOrder) {
      Set<String> <warning descr="Contents of collection 'set' are updated, but never queried">set</warning> = preserveOrder ? new LinkedHashSet<>() : new HashSet<>();
      set.add("foo");
      set.add("bar");
    }

    Object[] testAddToAnotherCollection(List<List<String>> list) {
      List<String> l = new ArrayList<>();
      // adding list to another collection could cause that list modification
      list.add(l);
      process(list);
      return l.toArray();
    }

    Object[] testRemoveFromAnotherCollection(List<List<String>> list) {
      List<String> <warning descr="Contents of collection 'l' are queried, but never updated">l</warning> = new ArrayList<>();
      list.remove(l);
      process(list);
      return l.toArray();
    }

    native void process(List<List<String>> list);

    void testPureMethod() {
      List<String> <warning descr="Contents of collection 'list' are queried, but never updated">list</warning> = new ArrayList<>();
      if(hasNull(list)) {
        System.out.println("has nulls!");
      }
    }

    // Purity is inferred
    static boolean hasNull(Collection<?> c) {
      for(Object o : c) {
        if(o == null) return true;
      }
      return false;
    }

    void testSomeList() {
      SomeList x = new SomeList();
      SomeList <warning descr="Contents of collection 'y' are updated, but never queried">y</warning> = new SomeList();
      x.add(1);
      y.add(2);
      // Calling unknown method should suppress the warning
      x.print();
    }

    void testForEach() {
      List<String> list = new ArrayList<>();
      list.add("foo");
      list.add("bar");
      list.forEach(System.out::println);
    }

    boolean testDoubleBrace(String key) {
      Map<String, String> map = new HashMap<>() {{
        put("foo", "bar");
        put("baz", "qux");
      }};
      return map.containsKey(key);
    }

    boolean testSeparateInitialization() {
      List<String> <warning descr="Contents of collection 'list' are queried, but never updated">list</warning>;
      list = new ArrayList<>();
      return list.isEmpty();
    }

    void testKeySet() {
      Map<String, String> <warning descr="Contents of collection 'map' are queried, but never updated">map</warning> = new HashMap<>();
      for(String s : map.keySet()) {
        System.out.println(s);
      }
    }

    private List<String> <warning descr="Contents of collection 'nonInitialized' are updated, but never queried">nonInitialized</warning>;

    void testNullCheck(String key) {
      if(nonInitialized == null) {
        nonInitialized = new ArrayList<>();
      }
      nonInitialized.add(key);
    }

    void testListIterator() {
      // IDEA-128168
      List<String> test = new ArrayList<String>();
      ListIterator<String> i = test.listIterator(0);
      i.add("hello!");
      System.out.println(i.next());
    }

    void testIterator() {
      List<String> <warning descr="Contents of collection 'test' are queried, but never updated">test</warning> = new ArrayList<String>();
      Iterator<String> i = test.iterator();
      while(i.hasNext()) {
        if(i.next() == null) {
          // Normally iterator cannot add, remove only. If collection is always empty (not updated in any other way),
          // this is useless anyways
          i.remove();
        }
      }
    }

    void add(TestPrivateClass tpc) {
      tpc.field.add("foo");
    }

    void add(TestPackageClass tpc) {
      tpc.field.add("foo");
    }

    void copyConstructors() {
      // IDEA-175455
      Map<String, String> sourceMap = new HashMap<>();
      sourceMap.put("foo", "bar");

      Map<String, String> <warning descr="Contents of collection 'destMap' are updated, but never queried">destMap</warning> = new HashMap<>(sourceMap);
      destMap.put("hello", "world");

      Collection<String> sourceList = new ArrayList<>();
      sourceList.add("hello");

      Collection<String> <warning descr="Contents of collection 'destList' are updated, but never queried">destList</warning> = new ArrayList<>(sourceList);
      destList.add("world");
    }

    private class TestPrivateClass {
      List<String> <warning descr="Contents of collection 'field' are updated, but never queried">field</warning> = new ArrayList<>();
    }

    class TestPackageClass {
      List<String> field = new ArrayList<>();
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
        boolean __(E e);
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

  int ignoredClasses() {
    BaseConstList<String> <warning descr="Contents of collection 'l1' are queried, but never updated">l1</warning> = new BaseConstList<String>(10, "foo");
    ConstList<String> l2 = new ConstList<String>(10, "foo");
    ChildConstList<String> l3 = new ChildConstList<String>(10, "foo");
    return l1.size()+l2.size()+l3.size();
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

class BaseConstList<T> extends AbstractList<T> {
  private int size;
  private T value;

  BaseConstList(int size, T value) {
    this.size = size;
    this.value = value;
  }

  @Override
  public T get(int index) {
    return value;
  }

  @Override
  public int size() {
    return size;
  }
}

class ConstList<T> extends BaseConstList<T> {
  ConstList(int size, T value) {super(size, value);}
}
class ChildConstList<T> extends ConstList<T> {
  ChildConstList(int size, T value) {super(size, value);}
}

class SomeList extends ArrayList<Integer> {
  void print() {
    System.out.println(this);
  }
}

class UnmodifiableTernaryTest {
  private final List<String> myList = new ArrayList<>();
  private final List<String> <warning descr="Contents of collection 'myList2' are queried, but never updated">myList2</warning> = new ArrayList<>();

  void add() {
    myList.add("foo");
  }

  List<String> get(boolean b) {
    return Collections.unmodifiableList(b ? myList : myList2);
  }
}

class InLambdaTest {
  void test() {
    List<String> <warning descr="Contents of collection 'listForLambda' are updated, but never queried">listForLambda</warning> = new ArrayList<>();
    IntStream.range(0, 100).mapToObj(String::valueOf).forEach(e -> listForLambda.add(e));
  }
}