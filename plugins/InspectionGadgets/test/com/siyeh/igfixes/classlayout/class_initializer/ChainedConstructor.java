class ChainedConstructor {
    String s;
    String t;

    ChainedConstructor(String s) {
        this.s = s;
    }

    ChainedConstructor() {
        this("a");
    }

    {<caret>
        System.out.println();
        t = "b";
    }
}