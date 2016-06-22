class Test1 {
    void foo(){}
    {
        String str = "effectively final string";
        String str1 = "effectively final string";
        Comparable<String> a = <caret>o->{
            System.out.println(str1);
            new Runnable() {
                @Override
                public void run() {
                    System.out.println(o);
                    System.out.println(str);
                }
            }.run();
            return 0;
        };
    }
}