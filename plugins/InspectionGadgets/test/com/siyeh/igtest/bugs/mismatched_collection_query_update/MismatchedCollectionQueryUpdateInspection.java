package com.siyeh.igtest.bugs.mismatched_collection_query_update;

import java.util.*;
import java.io.FileInputStream;

public class MismatchedCollectionQueryUpdateInspection {
    private Set foo = new HashSet();
    private Set foo2 = new HashSet();
    private Set bar ;
    private Set bar2 = new HashSet(foo2);
    private Set bal ;

    public void bar()
    {
        foo.add(new Integer(bar.size()));
        final boolean found = foo.add(new Integer(bar2.size()));
    }

    public void bar2()
    {
        final List barzoom = new ArrayList(3);
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
}
