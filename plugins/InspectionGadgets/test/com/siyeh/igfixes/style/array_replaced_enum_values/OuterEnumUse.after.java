class OuterEnumUse {

    public static void foo() {
        testMethod(OuterEnum.TestEnum.values());
    }
    private static void testMethod(OuterEnum.TestEnum[] values) {
    }
}