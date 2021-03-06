import org.jetbrains.annotations.NonNls;

class Test {
    interface X {
        @NonNls String getInterfaceString();
    }

    @NonNls String getLocalString() { return "s"; }

    void test(X x){
        final String s1 = x.getInterfaceString();
        if (s1.equals("test")) {
            System.out.println();
        }

        final String s2 = getLocalString();
        if (s2.equals("test")) {
            System.out.println();
        }
    }

}