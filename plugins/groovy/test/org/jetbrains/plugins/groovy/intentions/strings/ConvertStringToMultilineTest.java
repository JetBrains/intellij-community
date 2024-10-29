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

import com.intellij.codeInsight.intention.IntentionAction;
import junit.framework.TestCase;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.intentions.GrIntentionTestCase;
import org.jetbrains.plugins.groovy.intentions.conversions.strings.ConvertStringToMultilineIntention;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Max Medvedev
 */
public class ConvertStringToMultilineTest extends GrIntentionTestCase {
  public ConvertStringToMultilineTest() {
    super(ConvertStringToMultilineIntention.getHint());
  }

  public void testPlainString() {
    doTextTest("print 'ab<caret>c'", "print '''abc'''");
  }

  public void testGString() {
    doTextTest("print \"ab<caret>c\"", "print \"\"\"abc\"\"\"");
  }

  public void testPlainString2() {
    doTextTest("print 'a\\nb<caret>c'", "print '''a\nbc'''");
  }

  public void testGString2() {
    doTextTest("print \"a\\nb<caret>c\"", "print \"\"\"a\nbc\"\"\"");
  }

  public void testGString3() {
    doTextTest("print \"a\\nb${a<caret>}c\"", "print \"\"\"a\nb${a}c\"\"\"");
  }

  public void testAlreadyMultiline() {
    doAntiTest("print \"\"\"a<caret>bc\"\"\"");
  }

  public void testGString4() {
    doTextTest("""
                 print "ab<caret>c\\$ $x"
                 """, """
                 print ""\"abc\\$ $x""\"
                 """);
  }

  public void doSelectionTest(String before, String after) {
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, before);

    TestCase.assertTrue(myFixture.getEditor().getSelectionModel().hasSelection());
    final IntentionAction intention = new ConvertStringToMultilineIntention();
    TestCase.assertTrue(intention.isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));
    intention.invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());

    myFixture.checkResult(after);
  }

  public void testSimpleConcatenation() {
    doSelectionTest("""
                      print <selection>'foo\\n' +
                            'bar'</selection>
                      """, """
                      print <selection>'''foo
                      bar'''</selection>
                      """);
  }

  public void testGStringConcatenation() {
    doSelectionTest("""
                      print <selection>"foo${x}\\n" +
                            "bar"</selection>
                      """, """
                      print <selection>""\"foo${x}
                      bar""\"</selection>
                      """);
  }

  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "intentions/convertToMultiline/";
  }
}
