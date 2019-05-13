class G {
  private Map<String, Map<String, String>> commits = [:]

  int register(String virtualHash, Map<String, String> commitDetails) {
    commits[virtualHash] = commitDetails
  }
}