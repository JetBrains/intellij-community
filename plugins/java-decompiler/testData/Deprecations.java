public class Deprecations {
  /** @deprecated */
  public int byComment;

  @Deprecated
  public int byAnno;

  /** @deprecated */
  public void byComment() { }

  @Deprecated
  public void byAnno() { }

  /** @deprecated */
  public static class ByComment { }

  @Deprecated
  public static class ByAnno { }
}