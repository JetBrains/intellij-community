class AnonymousClassArgument {
  {
    Thread t = new Thread(AnonymousClassArgument::<caret>run) {} ;
  }

    private static void run() {
    }
}