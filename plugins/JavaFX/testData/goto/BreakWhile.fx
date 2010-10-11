function run(args: String[]) {
  while (true) {
    if (args.size() != 0) {
      br<caret>eak;
    }
    bar();
  }
  foo();
}