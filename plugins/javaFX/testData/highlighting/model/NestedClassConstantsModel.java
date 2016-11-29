package model;

public class NestedClassConstantsModel {
    public enum Cardinality {
        SINGLE, MULTI
    }

    public static class Nested {
        public static class Direction {
            public static final Direction UP = new Direction();
            public static final Direction DOWN = new Direction();
        }
    }
}