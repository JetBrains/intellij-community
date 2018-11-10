class AnonymousClassArgument {
  {
    Thread t = new Thread(() <caret>-> {}) {} ;
  }
}