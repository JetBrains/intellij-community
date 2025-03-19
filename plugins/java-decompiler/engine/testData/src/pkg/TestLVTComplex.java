package pkg;

import java.util.ArrayList;

public class TestLVTComplex {
    public static void main() {
        int[] x = new int[5];
        for (int y : x)
            ;
        for (int y : x) {
            System.out.println("asdf");
        }
        ArrayList<Object> x1 = new ArrayList<Object>();
        for (Object y : x1)
            ;
        for (Object y : x1) {
            int[] x2 = new int[10];
            for (int y2 : x2)
                ;
            for (int y2 : x2) {
                System.out.println("asdf");
            }
            System.out.println("asdf");
        }
        switch (Bob.HI) {
        case HI:
            System.out.println("HI");
            break;
        case LO:
            System.out.println("LO");
            break;
        }
        if (Bob.HI == Bob.HI) {
            String a = "a";
        } else {
            String b = "b";
        }
        String a2;
        if (Bob.HI == Bob.HI) {
            a2 = "a";
        } else {
            a2 = "b";
        }
        if (Bob.HI == Bob.HI) {
            a2 = "a";
        }
        System.out.println(a2);

    }

    private static enum Bob {
        HI, LO;
        static {
            for (Bob b : Bob.values()) {
                for (Bob c : values()) {
                    for (Bob d : values()) {
                        if (b == c)
                            System.out.println("Asdf");
                    }
                }
            }
        };
    }
}
