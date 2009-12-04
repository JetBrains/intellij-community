package com.siyeh.igtest.style.field_final;
import java.awt.*; import java.awt.event.KeyEvent;
public class FieldMayBeFinal {

    private static String string;
    private static int i;

    static {
        string = null;
    }
    static {
        string = null;
    }

    private String other;
    {
        other = null;
    }
    private String ss;
    {
        ss = "";
    }
    {
        ss = "";
    }

    private int number;
    private String s;
    public FieldMayBeFinal() {
        s = "";
        number = 0;
    }

    public FieldMayBeFinal(int number) {
        new Runnable() {

            public void run() {
                s = "";

            }
        };
        s = "";
        this.number = number;
    }

    private String utterance = "asdfas";

    private class Action {
        public void foo() {
            utterance = "boo!";
        }
    }

    private String unused;

    private static class Boom {
        private String notFinal;

        private static String two;

        static {
            if (1 == 2) {
                two = "other";
            }
        }

        Boom(boolean b) {
            if (b) {
                notFinal = "";
            }
        }

    }

    private static boolean flag = true;

    private static final KeyEventPostProcessor processor = new KeyEventPostProcessor() {
        public boolean postProcessKeyEvent(KeyEvent event) {
            flag = event.isAltDown();
            return false;
        }
    };

    static class Test
    {

        public static void main(String[] args)
        {
            Inner inner = new Inner();
            inner.val = false;
        }

        private static class Inner
        {
            private boolean val = true;
            private boolean pleaseTellMeIt = true;
        }
    }

    static class Test3 {
        private static String hostName;
        static {
            try {
                hostName = java.net.InetAddress.getLocalHost().getHostName();
            } catch (Exception ignored) {
                hostName = "localhost";
            }
        }
    }

    static class Test4 {
        private static String hostName;
        static {
            try {
                hostName = java.net.InetAddress.getLocalHost().getHostName();
            } catch (Exception ignored) {
                throw new RuntimeException();
            }
        }
    }

    static class DoubleAssignment {
        private String result;

        public DoubleAssignment() {
            result = "";
            result = "";
        }
    }

    static class IncrementInInitializers {
        private int i = 0;
        private final int j = i++;
    }

    static class AssigmentInForeach {
        private boolean b;
        private boolean c;

        AssigmentInForeach(int[] is) {
            b = false;
            for (int i : is) {
                b = c = i == 10;
            }
        }
    }

    static class StaticVariableModifiedInInstanceVariableInitializer {

        private static int COUNT = 0; // <<<<<< highlights as "can be made final"

        private final int count = COUNT++;

    }

}