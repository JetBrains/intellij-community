function run(args: String[]) {
  for (x in [1..10]) {
    if (args.size() < x) {
      conti<caret>nue;
    }
  }
}