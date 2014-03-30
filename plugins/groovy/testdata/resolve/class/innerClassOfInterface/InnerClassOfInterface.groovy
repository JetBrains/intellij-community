public interface IMappingPolicy {
  enum PolicyResult {DontImport, UseUndefined, UseAbsent, UseSpecified, UseNull }

  class PolicyResultData  {}
}

class S implements IMappingPolicy {
  def onUnmappedMasterData() {
    return new PolicyResu<ref>ltData()
  }
}