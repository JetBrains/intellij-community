package com.siyeh.igtest.style.field_final;

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
}