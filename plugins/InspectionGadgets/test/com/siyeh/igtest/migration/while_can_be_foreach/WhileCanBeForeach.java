package com.siyeh.igtest.migration.while_can_be_foreach;

import java.util.*;

public class WhileCanBeForeach {

    public int baz() {
        int total = 0;
        final List ints = new ArrayList();
        Iterator iterator = ints.iterator();
        <warning descr="'while' loop replaceable with enhanced 'for'">while</warning> ( iterator.hasNext()) {
            total += (Integer) iterator.next();
        }
        return total;
    }

    public int bas() {
        int total = 0;
        final List ints = new ArrayList();
        Iterator iterator = ints.iterator();
        <warning descr="'while' loop replaceable with enhanced 'for'">while</warning> ( iterator.hasNext()) {
            total += (Integer) iterator.next();
        }
        iterator = ints.iterator(); // write use here
        return total;
    }

    public int xxx() {
        int total = 0;
        final List ints = new ArrayList();
        Iterator<Integer> iterator = ints.iterator();
        while (iterator.hasNext()) {
            total += iterator.next();
        }
        System.out.println("iterator: " + iterator); // read use here
        return total;
    }

    void no(Collection pbps, Map tracksToPBP) {
        final Iterator pbpsIt = pbps.iterator();
        <warning descr="'while' loop replaceable with enhanced 'for'">while</warning> (pbpsIt.hasNext()) {
            final String pbp = (String) pbpsIt.next();
            final Iterator trackIt = it();
            while (trackIt.hasNext()) {
                final String trackElement = (String) trackIt.next();
                tracksToPBP.put(trackElement, pbp);
            }
        }
    }

    Iterator it() {
        return null;
    }

    private void qwe() {
        Iterator iter = new ArrayList().iterator();

        while (iter.hasNext()) {
            Object o = iter.next();
            if(o instanceof String)
                break;
        }

        while (iter.hasNext()) { // same iterator is used here
            Object o = iter.next();
        }
    }

  int strange() {
    int total = 0;
    final List ints = new ArrayList();
    ListIterator iterator = ints.listIterator();
    <warning descr="'while' loop replaceable with enhanced 'for'">while</warning> ( iterator.hasNext()) {
      final Object next = iterator.next();
      total += (Integer) next;
    }
    return total;
  }

    void foo(List<String> list, String newCd) {
        final ListIterator<String> iter = list.listIterator();
        while( iter.hasNext() )
        {
            final String cd = iter.next();
            if( cd.getBytes().equals( newCd.getBytes() ) )
            {
                iter.add( newCd );
                return;
            }
        }
    }

  void a() {
    List<String> list = new ArrayList();
    ListIterator<String> it = list.listIterator(10);
    //Intention here:
    while (it.hasNext()) { // don't warn because listIterator starts at index 10
      System.out.println(it.next());
    }
  }

  void b(List<String> list) {
    final var iterator = list.iterator();
    while (iterator.hasNext()) {
      System.out.println(iterator.next());
      final var iterator1 = iterator;
      System.out.println(iterator1);
    }
  }

  void c(List<String> strings) {
      Iterator<String> iterator = strings.listIterator();
      while (iterator.hasNext()) {
        System.out.println(iterator.next());
        iterator.remove();
      }
  }

  void insideTryBlockPositive1() {
    try {
      Iterator<String> iterator = Arrays.asList("1", "2").iterator();
      <warning descr="'while' loop replaceable with enhanced 'for'">while</warning> (iterator.hasNext()) {
        System.out.println(iterator.next());
      }
    } catch (Exception e) {}
  }

  void insideTryBlockPositive2() {
    Iterator<String> iterator = Arrays.asList("1", "2").iterator();
    <warning descr="'while' loop replaceable with enhanced 'for'">while</warning> (iterator.hasNext()) {
      try {
        System.out.println("Some string");
      } catch (Exception e) {
      }
      System.out.println(iterator.next());
    }
  }

  void insideTryBlockNegative() {
    Iterator<String> iterator = Arrays.asList("1", "2").iterator();
    while (iterator.hasNext()) {
      try {
        System.out.println(iterator.next());
      } catch (Exception e) {
      }
    }
  }

  void insideInnerWhilePositive() {
    Iterator<Integer> iterator = Arrays.asList(1, 2, 3, 4, 5, 6).iterator();
    <warning descr="'while' loop replaceable with enhanced 'for'">while</warning> (iterator.hasNext()) {
      int i = 0;
      while (i != 3) {
        i++;
        System.out.println("Number " + i);
      }
      Integer next = iterator.next();
      System.out.println(next);
    }
  }

  void insideInnerWhileNegative() {
    Iterator<Integer> iterator = Arrays.asList(1, 2, 3, 4, 5, 6).iterator();
    while (iterator.hasNext()) {
      int i = 0;
      while (i != 3) {
        Integer next = iterator.next();
        System.out.println(next);
      }
    }
  }
}
class Base implements Iterable<String> {
  @Override
  public Iterator<String> iterator() {
    return null;
  }
}

class Sub extends Base {
  @Override
  public Iterator<String> iterator() {
    ArrayList<String> strings = new ArrayList<String>();
    Iterator<String> superIterator = super.iterator();

    while (superIterator.hasNext()) {
      String str = superIterator.next();

      strings.add(str + str);
    }

    return strings.iterator();
  }
}