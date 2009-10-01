label:
for (i in [1,2,3]) {
  for (j in [1,2,3]){
    for (k in [1,2,3]) {
      continue;
    }
    break label;
  }
  continue label;
}

break label;