class UseKotlinSubclassesOfMappedTypes {
    void test() {
        Iterable<String> iterable = new KotlinIterableInterfaceTest();
        Comparable<Integer> comparable = new KotlinComparableTest();
    }
}