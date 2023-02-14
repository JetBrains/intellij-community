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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class UseOfPropertiesAsHashtableInspectionTest extends LightJavaInspectionTestCase {

  public void testProperties() {
    doMemberTest("""
                   public void testThis(java.util.Properties p, java.util.Properties p2, java.util.Map m, java.util.Map<String, String> m2) {
                     p./*Call to 'Hashtable.get()' on properties object*/get/**/("foo");
                     p.getProperty("foo");
                     p./*Call to 'Hashtable.put()' on properties object*/put/**/("foo", "bar");
                     p.setProperty("foo", "bar");
                     p./*Call to 'Hashtable.putAll()' on properties object*/putAll/**/(m);
                     p.putAll(m2);
                     p.putAll(p2);
                   }
                   """);
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new UseOfPropertiesAsHashtableInspection();
  }
}
