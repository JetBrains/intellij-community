function run(args: String[]) {
  for (x in [1..200]) {
    if (args.size() != 0) {
      break;
    }
    bar();
  }
  <caret>foo();
}