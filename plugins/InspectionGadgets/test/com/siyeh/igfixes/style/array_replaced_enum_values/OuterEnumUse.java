class OuterEnumUse {

    public static void foo() {
        testMethod(new OuterEnum.TestEnum[]{OuterEnum.TestEnum.<caret>A, OuterEnum.TestEnum.B, OuterEnum.TestEnum.C});
    }
    private static void testMethod(OuterEnum.TestEnum[] values) {
    }
}