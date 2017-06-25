/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ipp.collections;

import com.siyeh.ipp.IPPTestCase;

/**
 * @author Bas Leijdekkers
 */
public class ReplaceWithArraysAsListIntentionJdk9Test extends IPPTestCase {

  public void testBrokenCode() {
    doTest(
      "import java.util.*;" +
      "class X {" +
      "  List<String> f() {" +
      "    return Collections.emptyList(\"text\"/*_Replace with 'java.util.List.of()'*/);" +
      "  }" +
      "}",

      "import java.util.*;" +
      "class X {" +
      "  List<String> f() {" +
      "    return List.of(\"text\");" +
      "  }" +
      "}"
    );
  }

  public void testNullArgument() {
    doTest(
      "import java.util.*;" +
      "class X {" +
      "  List<String> f() {" +
      "    return Collections.emptyList(null/*_Replace with 'java.util.Arrays.asList()'*/, null);" +
      "  }" +
      "}",

      "import java.util.*;" +
      "class X {" +
      "  List<String> f() {" +
      "    return Arrays.asList(null, null);" +
      "  }" +
      "}");
  }

  public void testNullableArgument() {
    doTest(
      "import java.util.*;" +
      "class X {" +
      "  List<String> f(@org.jetbrains.annotations.Nullable String a) {" +
      "    return Collections.emptyList(a/*_Replace with 'java.util.Arrays.asList()'*/);" +
      "  }" +
      "}",

      "import java.util.*;" +
      "class X {" +
      "  List<String> f(@org.jetbrains.annotations.Nullable String a) {" +
      "    return Arrays.asList(a);" +
      "  }" +
      "}");
  }

  public void testReplaceSingletonList() {
    doTest(
      "import java.util.*;" +
      "class X {" +
      "  List<String> f() {" +
      "    return Collections.singletonList(\"text\"/*_Replace with 'java.util.List.of()'*/);" +
      "  }" +
      "}",

      "import java.util.*;" +
      "class X {" +
      "  List<String> f() {" +
      "    return List.of(\"text\");" +
      "  }" +
      "}"
    );
  }

  public void testReplaceSingleton() {
    doTest(
      "import java.util.*;" +
      "class X {" +
      "  Set<String> f() {" +
      "    return Collections.singleton(\"text\"/*_Replace with 'java.util.Set.of()'*/);" +
      "  }" +
      "}",

      "import java.util.*;" +
      "class X {" +
      "  Set<String> f() {" +
      "    return Set.of(\"text\");" +
      "  }" +
      "}"
    );
  }

  public void testReplaceEmptyMap() {
    doTest(
      "import java.util.*;" +
      "class X {" +
      "  Map<String, String> f() {" +
      "    return Collections.emptyMap(\"key\", \"text\"/*_Replace with 'java.util.Map.of()'*/);" +
      "  }" +
      "}",

      "import java.util.*;" +
      "class X {" +
      "  Map<String, String> f() {" +
      "    return Map.of(\"key\", \"text\");" +
      "  }" +
      "}"
    );
  }
}
