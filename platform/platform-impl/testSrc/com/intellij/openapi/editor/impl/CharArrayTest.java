/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl;

import static org.junit.Assert.*;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jmock.Expectations;
import org.jmock.api.Invocation;
import org.jmock.lib.action.CustomAction;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.rules.TestWatchman;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.runners.model.FrameworkMethod;

import java.lang.annotation.*;

/**
 * @author Denis Zhdanov
 * @since 03/01/2011
 */
public class CharArrayTest {

  @Rule
  public TestWatchman configReader = new TestWatchman() {
    @Override
    public void starting(FrameworkMethod method) {
      Config config = method.getAnnotation(Config.class);
      if (config != null) {
        myConfig = config;
      }
    }
  };
  
  private CharArray myArray;
  private Config myConfig;
  private Mockery myMockery;
  private DocumentImpl myDocument;
    
  @Before
  public void setUp() {
    myMockery = new JUnit4Mockery() {{
      setImposteriser(ClassImposteriser.INSTANCE);
    }};
    myDocument = myMockery.mock(DocumentImpl.class);
    
    myMockery.checking(new Expectations() {{
      allowing(myDocument).getTextLength(); will(new CustomAction("getTextLength") {
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          return myArray.length();
        }
      });
    }});
    
    init(10);
    if (myConfig != null) {
      myArray.insert(myDocument, myConfig.text(), 0);
      myArray.setDeferredChangeMode(myConfig.deferred());
    }
  }
  
  @After
  public void checkExpectations() {
    myMockery.assertIsSatisfied();
  }
  
  @Config(text = "1234", deferred = true)
  @Test 
  public void deferredReplace() {
    replace(1, 3, "abc");
    checkText("1abc4");
    assertTrue(myArray.hasDeferredChanges());
    
    replace(2, 3, "XY");
    checkText("1aXYc4");
    assertTrue(myArray.hasDeferredChanges());
    
    replace(3, 6, "ABC");
    checkText("1aXABC");
    assertTrue(myArray.hasDeferredChanges());
    
    myArray.setDeferredChangeMode(false);
    checkText("1aXABC");
    assertFalse(myArray.hasDeferredChanges());
  }
  
  private void init(int size) {
    myArray = new CharArray(size) {
      @Override
      protected DocumentEvent beforeChangedUpdate(DocumentImpl subj, int offset, CharSequence oldString, CharSequence newString,
                                                  boolean wholeTextReplaced)
      {
        return new DocumentEventImpl(subj, offset, oldString, newString, LocalTimeCounter.currentTime(), wholeTextReplaced);
      }

      @Override
      protected void afterChangedUpdate(DocumentEvent event, long newModificationStamp) {
      }
    };
  }

  private void checkText(@NotNull String expected) {
    // Test as a whole.
    assertEquals(expected, myArray.toString());
    assertEquals(expected.length(), myArray.length());
    
    // Test 'charAt()'.
    for (int i = 0; i < expected.length(); i++) {
      if (expected.charAt(i) != myArray.charAt(i)) {
        fail(String.format(
          "Detected incorrect 'charAt()' processing for deferred changes. Text: '%1$s'. Expected to get symbol '%2$c' "
          + "(numeric value %2$d) at index %3$d but actual symbol is '%4$c' (numeric value %4$d)",
          expected, (int)expected.charAt(i), i, (int)myArray.charAt(i)));
      }
      assertEquals(expected.charAt(i), myArray.charAt(i));
    }
    
    // Test 'substring()'.
    for (int start = 0; start < myArray.length() - 1; start++) {
      for (int end = start; end < myArray.length(); end++) {
        if (!expected.substring(start, end).equals(myArray.substring(start, end).toString())) {
          fail(String.format(
            "Detected incorrect 'substring()' processing for deferred changes. Text: '%s', expected to get substring '%s' for "
            + "interval [%d; %d) but got '%s'", expected, expected.substring(start, end), start, end, myArray.substring(start, end)
          ));
        }
      }
    }

    // Test subSequence().
    checkSubSequence(expected, myArray, new Stack<Pair<Integer, Integer>>());
  }
  
  private void checkSubSequence(@NotNull String expected, @NotNull CharSequence actual,
                                       @NotNull Stack<Pair<Integer, Integer>> history)
  {
    assertEquals(expected.length(), actual.length());
    for (int i = 0; i < expected.length(); i++) {
      char expectedChar = expected.charAt(i);
      char actualChar = actual.charAt(i);
      if (expectedChar != actualChar) {
        fail(String.format(
          "Detected incorrect charAt() processing for result of subSequence() with deferred changes. Original text: '%s', "
          + "actual subSequence text: '%s', index: %d, expected symbol: '%c', actual symbol: '%c', subSequence history: %s",
          myArray.toString(), expected, i, expectedChar, actualChar, history
        ));
      }
    }
    if (!expected.equals(actual.toString())) {
      fail(String.format(
        "Detected incorrect toString() processing for result of subSequence() with deferred changes. Original text: '%s', "
        + "expected subSequence text: '%s', actual subSequence text: '%s', subSequence history: %s",
        myArray.toString(), expected, actual.toString(), history
      ));
    }
    assertEquals(expected, actual.toString());
    for (int start = 0; start < expected.length(); start++) {
      for (int end = start; end < expected.length(); end++) {
        history.push(new Pair<Integer, Integer>(start, end));
        checkSubSequence(expected.substring(start, end), actual.subSequence(start, end), history);
        history.pop();
      }
    }
  }
  
  private void replace(int startOffset, int endOffset, String newText) {
    myArray.replace(
      myDocument, startOffset, endOffset, myArray.substring(startOffset, endOffset), newText, LocalTimeCounter.currentTime(),
      startOffset == 0 && endOffset == myArray.length()
    );
  }
  
  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  private @interface Config {
    String text() default "";
    boolean deferred() default false;
  }
}
