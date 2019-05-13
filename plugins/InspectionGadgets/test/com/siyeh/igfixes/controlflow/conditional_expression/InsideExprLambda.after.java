class Test {
  {
    Runnable r = () -> {
        if (true) "a";
        else "b";
    };
  }
}