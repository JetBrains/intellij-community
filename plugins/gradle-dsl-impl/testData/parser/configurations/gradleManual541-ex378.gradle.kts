configurations {
  // A configuration meant for consumers that need the API of this component
  create("exposedApi") {
    // This configuration is an "outgoing" configuration, it's not meant to be resolved
    canBeResolved = false
    // As an outgoing configuration, explain that consumers may want to consume it
    canBeConsumed = true
  }
  // A configuration meant for consumers that need the implementation of this component
  create("exposedRuntime") {
    canBeResolved = false
    canBeConsumed = true
  }
}
