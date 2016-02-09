/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightInspectionTestCase;
import junit.framework.TestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class OptionalContainsCollectionInspectionTest extends LightInspectionTestCase {

  public void testArray() {
    doTest("import java.util.Optional;" +
           "class X {" +
           "  Optional</*'Optional' contains array 'String[]'*/String[]/**/> a() {" +
           "    return Optional.empty();" +
           "  }" +
           "}");
  }

  public void testMap() {
    doTest("import java.util.Optional;" +
           "import java.util.Map;" +
           "class X {" +
           "  Optional</*'Optional' contains collection 'Map<String, String>'*/Map<String, String>/**/> a() {" +
           "    return Optional.empty();" +
           "  }" +
           "}");
  }

  public void testNoWarning() {
    doTest("import java.util.Optional;" +
           "class X {" +
           "  Optional<String> a() {" +
           "    return Optional.empty();" +
           "  }" +
           "}");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new OptionalContainsCollectionInspection();
  }

  @Override
  protected String[] getEnvironmentClasses() {
    return new String[]{
      "package java.util;" +
      "public final class Optional<T> {" +
      "    public static<T> Optional<T> empty() {" +
      "        return new Optional<>();" +
      "    }" +
      "}"
    };
  }
}