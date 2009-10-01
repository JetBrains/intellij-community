for (i in [1, 2, 3]) {
  for (j in [1, 2, 3]) {
    for (k in [1, 2, 3]) {
      continue;
    }
    break label;
  }
  continue label;
}

while (true) {
  break lab<ref>el;
}

label: print "wow"