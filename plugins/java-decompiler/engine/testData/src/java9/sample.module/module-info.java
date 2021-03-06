import sample.pkg1.TestModuleAnno;

@TestModuleAnno("...")
module sample.module {
  requires java.desktop;

  uses java.util.spi.ToolProvider;

  provides sample.pkg1.TestService with sample.pkg1.TestServiceImpl;

  exports sample.pkg1;

  exports sample.pkg2 to java.base;

  opens sample.pkg1;

  opens sample.pkg2 to java.base;
}
