class Comment {
  void x() {
      label:
      while (true) {
        int x = new Random().nextInt();
          switch (x) {
              case 1:
                  System.out.println();
                  break label; //comment
              case 2:
                  System.out.println();
                  break;
              case 3:
                  System.out.println();
                  break;
          }
      }
  }
}