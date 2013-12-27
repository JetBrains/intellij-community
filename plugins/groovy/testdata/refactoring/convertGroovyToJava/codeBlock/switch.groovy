String[] commands = ['abc']
for (String command : commands) {
  switch (command) {
    case "abc":
      print 1
    case "start":
      return 4
    case "next":
      continue;
    default:
      return 0;
  }
}
