module sample.module {
  requires java.base;

  uses java.util.spi.ToolProvider;

  provides test.TestService with test.TestServiceImpl;

  exports test;

  exports test2 to java.base;

  opens test;

  opens test2 to java.base;
}