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
package org.jetbrains.plugins.groovy.intentions.strings;

import org.jetbrains.plugins.groovy.intentions.GrIntentionTestCase;
import org.jetbrains.plugins.groovy.intentions.conversions.strings.RemoveUnnecessaryEscapeCharactersIntention;

/**
 * @author Max Medvedev
 */
public class RemoveUnnecessaryEscapedCharactersTest extends GrIntentionTestCase {
  public RemoveUnnecessaryEscapedCharactersTest() {
    super(RemoveUnnecessaryEscapeCharactersIntention.HINT);
  }

  public void testSimple() {
    doTextTest("""
                 print 'a<caret>b\\"c'
                 """, """
                 print 'a<caret>b"c'
                 """);
  }

  public void testAntiSimple() {
    doAntiTest("""
                  print 'a<caret>b\\'c'""");
  }

  public void testGString() {
    doTextTest("""
                 html ""\"
                 <p>D\\na<caret>ta: ${new Date().format("dd-MM-yyyy hh:mm")}<br/>
                 Totem: $kiosk.name
                  ""\"
                 """, """
                 html ""\"
                 <p>D
                 ata: ${new Date().format("dd-MM-yyyy hh:mm")}<br/>
                 Totem: $kiosk.name
                  ""\"
                 """);
  }
}
