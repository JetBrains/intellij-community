package records;

public record TestHideConstructorRecordDifferentTypes(String a, int... l) {
    public TestHideConstructorRecordDifferentTypes(String a, int l) {
        this(a, new int[]{l});
    }
}
