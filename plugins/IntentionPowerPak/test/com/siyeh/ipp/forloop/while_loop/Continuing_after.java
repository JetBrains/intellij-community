class Continuing {
  void testFor() {
      int i=0;
      while (i<10) {
        if(i == 5) {
            i++;
            continue;
        }
        System.out.println(i);
          i++;
      }
  }
}