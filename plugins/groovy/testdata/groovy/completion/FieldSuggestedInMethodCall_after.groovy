class ProjectBuilder {
  final def project;

  public def buildChunks() {
    build(project<caret>, null)
  }

  def build(def p, String s) {}

}
