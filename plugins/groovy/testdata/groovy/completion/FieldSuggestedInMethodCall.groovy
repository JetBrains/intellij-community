class ProjectBuilder {
  final def project;

  public def buildChunks() {
    build(proj<caret>, null)
  }

  def build(def p, String s) {}

}
