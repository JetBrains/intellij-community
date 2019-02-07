class ExplititTypeArguments2 {
  void x(boolean x) {
    if ((x ? ExplititTypeArguments2.<Boolean>a() : (x ? (Boolean) a() : ExplititTypeArguments2.<Boolean>a())).booleanValue()) ;
  }

  private static <T> T a() {
    return null;
  }
}