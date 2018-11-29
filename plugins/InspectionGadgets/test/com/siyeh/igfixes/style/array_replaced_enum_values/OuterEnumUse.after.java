class OuterEnumUse {

    public static void foo() {
        testMethod(TestEnum.values());
    }
    private static void testMethod(OuterEnum.TestEnum[] values) {
    }
}