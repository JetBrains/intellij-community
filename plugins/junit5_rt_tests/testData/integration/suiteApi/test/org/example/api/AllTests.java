package org.example.api;

import org.example.impl.FirstTest;
import org.example.impl.SecondTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({FirstTest.class, SecondTest.class})
public class AllTests {
}