package pkg;

public class TestSwitchClassReferencesFastExitEcj {
    public static void testObject(Object o) {
        Task:
        while (true) {
            for (int i = 0; i < o.hashCode(); i++) {
                switch (o) {
                    case String s:
                        System.out.println("s");
                        System.exit(0);
                        break;
                    case Integer in:
                        System.out.println("ii");
                        continue Task;
                    case Object ob:
                        System.out.println("s");
                        break Task;
                }
            }
        }
    }

    public static void testObject2(Object o) {
        Task:
        while (true) {
            for (int i = 0; i < o.hashCode(); i++) {
                switch (o) {
                    case String s -> {
                        System.out.println("s");
                        System.exit(0);
                        break;
                    }
                    case Integer in -> {
                        System.out.println("ii");
                        continue Task;
                    }
                    case Object ob -> {
                        System.out.println("s");
                        break Task;
                    }
                }
            }
        }
    }

}
