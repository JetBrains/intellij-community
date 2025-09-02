package org.example.api;

import org.example.impl.MyTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({MyTest.class})
public class SmokeSuite {
}