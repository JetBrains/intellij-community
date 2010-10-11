function run(args: String[]) {
  for (x in [1..200]) {
    if (args.size() != 0) {
      br<caret>eak;
    }
    bar();
  }
  foo();
}