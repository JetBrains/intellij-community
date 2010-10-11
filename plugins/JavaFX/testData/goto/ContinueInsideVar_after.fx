function run(args: String[]) {
  var a = <caret>for (x in [1..10]) {
    if (args.size() != 0) {
      continue;
    }
    args;
  }
}