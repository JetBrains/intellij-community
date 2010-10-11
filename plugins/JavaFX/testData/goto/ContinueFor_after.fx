function run(args: String[]) {
  <caret>for (x in [1..10]) {
    if (args.size() < x) {
      continue;
    }
  }
}