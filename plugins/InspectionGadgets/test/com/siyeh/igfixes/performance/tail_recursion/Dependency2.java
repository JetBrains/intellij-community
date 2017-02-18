class Dependency2 {

  private static boolean intersect(@NotNull Set<String> ids1, @NotNull Set<String> ids2) {
    if (ids1.size() > ids2.size()) return intersect<caret>(ids2, ids1);
    for (String id : ids1) {
      if (ids2.contains(id)) return true;
    }
    return false;
  }
}