public enum TestEnum {
    TEST_ENUM_ENTRY("test enum entry");
    private final String name;

    TestEnum(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}