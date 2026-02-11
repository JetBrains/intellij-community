package com.intellij.configurationScript

import org.junit.Ignore
import org.junit.runner.RunWith
import org.junit.runners.Suite

@Ignore("Manual only")
@RunWith(Suite::class)
@Suite.SuiteClasses(
  ConfigurationFileTest::class,
  ComponentStateTest::class,
  ConfigurationSchemaTest::class,
  PropertyValueReaderTest::class
)
class ConfigurationFileTestSuite

