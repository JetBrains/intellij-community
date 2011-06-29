package com.siyeh.igtest.style.field_final;
import java.awt.*; import java.awt.event.KeyEvent;import java.io.File;import java.io.IOException;
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

        /*AssigmentInForeach(int[] is) {
            b = false;
            for (int i : is) {
                b = c = i == 10;
            }
        }*/
    }

    static class StaticVariableModifiedInInstanceVariableInitializer {

        private static int COUNT = 0; // <<<<<< highlights as "can be made final"

        private final int count = COUNT++;

    }

    static class FalsePositive1 {
        private int i;

        FalsePositive1() {
            System.out.println(i);
            i = 1;
        }
    }
}
class NotFinal {
    private static final NotFinal INSTANCE = new NotFinal();

    private boolean isInitialized;

    private NotFinal() {
        isInitialized = false;
    }

    public static synchronized void initialize() {
        INSTANCE.isInitialized = true;
    }
}
class AAA {
    private String test;

    public AAA(int num) {
        if(num < 0) {
            return;
        }

        test = "ok";
    }

    public void feep() {
        System.out.println("test = " + test);
    }
}
class X {
    private int x;

    X() {
        x += 1;
    }
}
class XX {
    private int xx;
    XX() {
        if (true) {
            return;
        }
        xx = 1;
    }
}
class Y {
    private int x; // can be final
    private int y; // can be final
    private int z = y = 1; // can be final

    Y() {
        x = 1;
    }

    Y(String s) {
        this();
    }
}
class Z {
    Q q = new Q();
    class Q {
        private int i =1;
    }
    class R {
        {
            q.i = 2;
        }
    }
}
class ZX {
    private int i;

    ZX() {
        if (false && (i = 1) == 1) {

        };
    }
}
class InspectionTest
{
    private Object field;

    public InspectionTest(Object field)
    {
        this.field = field;
    }

    public InspectionTest()
    {
        try
        {
            File file = new File("test");
            if (!file.canRead())
            {
                return;
            }
            file.createNewFile();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }

        this.field = new Object();
    }
}
class InspectionTest2
{
    private Object field;

    public InspectionTest2(Object field)
    {
        this.field = field;
    }

    public InspectionTest2()
    {
        try
        {
            File file = new File("test");
            file.createNewFile();
            this.field = new Object();
        }
        catch (IOException e)
        {
            this.field = null;
        }
    }
}
class ActionMenu {
    private boolean myMnemonicEnabled;

    public ActionMenu(final boolean enableMnemonics) {
        myMnemonicEnabled = enableMnemonics;
    }

    private boolean isTopLevel() {
        return true;
    }

    public void setMnemonicEnabled(boolean enable) {
        myMnemonicEnabled = enable;
    }
}
