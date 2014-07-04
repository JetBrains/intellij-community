class Foo {
    @org.jetbrains.annotations.NonNls String[] myArray = new String[] {"text1", "text2"};
    @org.jetbrains.annotations.NonNls Stirng[] foo() {
        myArray = new String[] {"text3", "text4"};
        myArray[0] = "text5";

        return new String[] {"text6"};
    }
}