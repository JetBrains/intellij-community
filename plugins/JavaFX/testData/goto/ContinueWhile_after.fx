function run(args: String[]) {
  <caret>while (true) {
    if (args.size() != 0) {
      continue;
    }
  }
}