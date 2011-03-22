package com.siyeh.igtest.migration.while_can_be_foreach;

import java.util.*;

public class WhileCanBeForeachInspection {

    public int baz() {
        int total = 0;
        final List ints = new ArrayList();
        Iterator iterator = ints.iterator();
        while ( iterator.hasNext()) {
            total += (Integer) iterator.next();
        }
        return total;
    }

    public int bas() {
        int total = 0;
        final List ints = new ArrayList();
        Iterator iterator = ints.iterator();
        while ( iterator.hasNext()) {
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
        while (pbpsIt.hasNext()) {
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
    while ( iterator.hasNext()) {
      final Object next = iterator.next();
      total += (Integer) next;
    }
    return total;
  }

}
