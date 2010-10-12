function run(args: String[]) {
  while (true) {
    if (args.size() != 0) {
      break;
    }
    bar();
  }
  <caret>foo();
}