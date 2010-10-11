function run(args: String[]) {
  var a = for (x in [1..10]) {
    if (args.size() != 0) {
      break;
    }
    args;
  }
  <caret>bar();
}