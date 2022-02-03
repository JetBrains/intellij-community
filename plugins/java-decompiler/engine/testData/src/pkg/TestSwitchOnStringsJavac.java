package pkg;
public class TestSwitchOnStringsJavac {
    String s;
    static final String S = "";

    void noCase() {
        switch (getStr()) {
        }
    }

    void oneCase(String s) {
        System.out.println(1);
        switch (s) {
            case "xxx":
                System.out.println(2);
                break;
        }
        System.out.println(3);
    }

    void oneCaseWithDefault() {
        System.out.println(1);
        switch (s) {
            case "xxx":
                System.out.println(2);
                break;
            default:
                System.out.println(3);
                break;
        }
        System.out.println(4);
    }

    void multipleCases1() {
        System.out.println(1);
        switch (S) {
            case "xxx":
                System.out.println(2);
                break;
            case "yyy":
                System.out.println(3);
                break;
        }
        System.out.println(4);
    }

    void multipleCasesWithDefault1() {
        System.out.println(1);
        switch (getStr()) {
            case "xxx":
                System.out.println(2);
                break;
            case "yyy":
                System.out.println(3);
                break;
            default:
                System.out.println(4);
                break;
        }
        System.out.println(5);
    }

    void multipleCases2() {
        System.out.println(1);
        switch (S) {
            case "xxx":
                System.out.println(2);
                break;
            case "yyy":
                System.out.println(3);
                break;
            case "zzz":
                System.out.println(4);
                break;
        }
        System.out.println(5);
    }

    void multipleCasesWithDefault2() {
        System.out.println(1);
        switch (getStr()) {
            case "xxx":
                System.out.println(2);
                break;
            case "yyy":
                System.out.println(3);
                break;
            case "zzz":
                System.out.println(4);
                break;
            default:
                System.out.println(5);
                break;
        }
        System.out.println(6);
    }


    void combined() {
        System.out.println("started");
        if (s.length() > 0) {
            System.out.println();
            switch(s) {
                case "b" -> System.out.println(1);
                case "d" -> System.out.println(2);
                case "a" -> System.out.println(3);
                case "f" -> System.out.println(4);
                default -> System.out.println(Math.random());
            }
            System.out.println(s);
            combined();
        } else {
            try {
                switch (getStr()) {
                    case "h":
                    case "i":
                        while (s != null) {
                            try {
                                if (s.length() == 1) {
                                    System.out.println(s);
                                }
                            } catch (NullPointerException e) {
                                System.out.println(e.getMessage());
                            }
                        }
                        System.out.println(5);
                    case "j":
                    case "f":
                        System.out.println(6);
                        return;
                    default:
                        System.out.println(7);
                }
            } catch (NullPointerException e) {
                for (int i = 0; i < 10; i++) {
                    switch (getStr()) {
                        case S -> System.out.println(8);
                        default -> System.out.println(e.getMessage());
                    }
                }
                System.out.println(9);
            }
        }
        System.out.println("finished");
    }

    String getStr() {
        return "";
    }
}
