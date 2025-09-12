// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.example.api;

import org.example.impl.FirstTest;
import org.example.impl.SecondTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({FirstTest.class, SecondTest.class})
public class AllTests {
}