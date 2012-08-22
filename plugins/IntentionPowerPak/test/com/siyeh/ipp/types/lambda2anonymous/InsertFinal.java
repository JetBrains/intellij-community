class Test1 {
    void foo(){}
    {
        Comparable<String> a = <caret>o->{
            new Runnable() {
                @Override
                public void run() {
                    System.out.println(o);
                }
            }.run();
            return 0;
        };
    }
}