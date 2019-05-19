package com.intellij.configurationScript

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
  ConfigurationFileTest::class,
  ComponentStateTest::class,
  ConfigurationSchemaTest::class,
  PropertyValueReaderTest::class
)
class ConfigurationFileTestSuite

