class Dependency2 {

    private static boolean intersect(@NotNull Set<String> ids1, @NotNull Set<String> ids2) {
        while (true) {
            if (ids1.size() > ids2.size()) {
                Set<String> ids11 = ids1;
                ids1 = ids2;
                ids2 = ids11;
                continue;
            }
            for (String id : ids1) {
                if (ids2.contains(id)) return true;
            }
            return false;
        }
    }
}