/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Version;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

import static com.intellij.testFramework.UsefulTestCase.assertSameElements;

public class JdkBundleListTest {
  @Test
  public void testAddBundle() {
    JdkBundle jb0 = new JdkBundle(new File("/jb0"), "java", Pair.create(new Version(1, 8, 0), new Integer(0)), true, false);
    JdkBundle jb1 = new JdkBundle(new File("/jb1"), "java", Pair.create(new Version(1, 8, 0), new Integer(1)), false, false);
    JdkBundle jb2 = new JdkBundle(new File("/jb2"), "java", Pair.create(new Version(1, 8, 0), new Integer(2)), false, false);
    JdkBundle jb3 = new JdkBundle(new File("/jb3"), "java", Pair.create(new Version(1, 8, 0), new Integer(3)), false, false);
    JdkBundle jb4 = new JdkBundle(new File("/jb4"), "java", Pair.create(new Version(1, 8, 0), new Integer(4)), false, false);

    JdkBundle ob0 = new JdkBundle(new File("/ob0"), "openjdk", Pair.create(new Version(1, 8, 0), new Integer(0)), false, true);
    JdkBundle ob1 = new JdkBundle(new File("/ob1"), "openjdk", Pair.create(new Version(1, 8, 0), new Integer(1)), false, false);
    JdkBundle ob2 = new JdkBundle(new File("/ob2"), "openjdk", Pair.create(new Version(1, 8, 0), new Integer(2)), false, false);
    JdkBundle ob3 = new JdkBundle(new File("/ob3"), "openjdk", Pair.create(new Version(1, 8, 0), new Integer(3)), false, false);
    JdkBundle ob4 = new JdkBundle(new File("/ob4"), "openjdk", Pair.create(new Version(1, 8, 0), new Integer(4)), false, false);

    JdkBundleList bundleList = new JdkBundleList();

    bundleList.addBundle(jb3, false);
    bundleList.addBundle(ob1, false);
    bundleList.addBundle(jb2, false);
    bundleList.addBundle(ob2, false);
    bundleList.addBundle(jb0, true);
    bundleList.addBundle(ob0, true);
    bundleList.addBundle(jb1, false);
    bundleList.addBundle(ob4, false);
    bundleList.addBundle(jb4, false);
    bundleList.addBundle(ob3, false);

    assertSameElements(bundleList.toArrayList(), Arrays.asList(jb4, ob4, jb0, ob0));
  }
}