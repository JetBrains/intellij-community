/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.intellij.ide.severities;

import com.intellij.lang.annotation.HighlightSeverity;
import junit.framework.TestCase;
import org.jdom.Element;

public class HighlightSeveritiesTest extends TestCase {
  public void testSeveritiesMigration() {
    final Element element = new Element("temp");
    new HighlightSeverity(HighlightSeverity.ERROR.myName, 500).writeExternal(element);
    HighlightSeverity newSeverity = new HighlightSeverity(element);
    assertEquals(500, newSeverity.myVal);
  }
}