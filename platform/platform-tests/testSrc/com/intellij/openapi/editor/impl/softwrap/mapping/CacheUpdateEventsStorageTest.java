/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.softwrap.mapping;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.util.text.CharArrayUtil;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.action.CustomAction;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Denis Zhdanov
 * @since 12/01/2010
 */
public class CacheUpdateEventsStorageTest {

  private CacheUpdateEventsStorage myStorage;
  private Document myDocument;
  private Mockery myMockery;
  private String myText;
    
  @Before
  public void setUp() {
    myText = 
      "line1\n" +
      "line2\n" +
      "line3\n" +
      "line4\n" +
      "line5\n" +
      "line6\n" +
      "line7";
    
    myMockery = new JUnit4Mockery() {{
      setImposteriser(ClassImposteriser.INSTANCE);
    }};
    myDocument = myMockery.mock(Document.class);
    myMockery.checking(new Expectations() {{
      allowing(myDocument).getTextLength(); will(new CustomAction("getTextLength()") {
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          return myText.length();
        }
      });
      
      allowing(myDocument).getLineNumber(with(any(int.class))); will(new CustomAction("getLineNumber()") {
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          return getLineNumber((Integer)invocation.getParameter(0));
        }
      });

      allowing(myDocument).getLineStartOffset(with(any(int.class))); will(new CustomAction("getLineStartOffset()") {
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          return getLineStartOffset((Integer)invocation.getParameter(0));
        }
      });

      allowing(myDocument).getLineEndOffset(with(any(int.class))); will(new CustomAction("getLineEndOffset()") {
        @Override
        public Object invoke(Invocation invocation) throws Throwable {
          return getLineEndOffset((Integer)invocation.getParameter(0));
        }
      });
    }});

    myStorage = new CacheUpdateEventsStorage();
  }
  
  @After
  public void checkExpectations() {
    myMockery.assertIsSatisfied();
  }
  
  private int getLineNumber(int offset) {
    if (offset == 0) {
      return 0;
    }
    int line = -1;
    int currentOffset = 0;
    while (currentOffset < offset) {
      line++;
      currentOffset = CharArrayUtil.shiftForwardUntil(myText, currentOffset, "\n") + 1;
      if (currentOffset == offset) {
        line++;
      }
    }
    return line;
  }

  private int getLineStartOffset(int line) {
    if (line == 0) {
      return 0;
    }
    int lineStartOffset = 0;
    for (; line > 0; line--) {
      lineStartOffset = CharArrayUtil.shiftForwardUntil(myText, lineStartOffset, "\n") + 1;
    }
    return lineStartOffset;
  }

  private int getLineEndOffset(int line) {
    int lineEndOffset = -1;
    for (; line >= 0; line--) {
      lineEndOffset = CharArrayUtil.shiftForwardUntil(myText, lineEndOffset + 1, "\n") + 1;
    }
    lineEndOffset--;
    return lineEndOffset;
  }
  
  @Test
  public void newEventLaysBeforeSingleRegistered() {
    int offset = myDocument.getLineStartOffset(2) + 2;
    change(offset, "AB");
    assertEquals(1, myStorage.getEvents().size());
    
    change(1, "12");
    checkOrder();
  }

  @Test
  public void newEventLaysBetweenRegistered() {
    int offset = myDocument.getLineStartOffset(5) + 2;
    change(offset, "AB");
    assertEquals(1, myStorage.getEvents().size());
    checkOrder();
    
    change(1, "CD");
    assertEquals(2, myStorage.getEvents().size());
    checkOrder();

    offset = myDocument.getLineStartOffset(3) + 2;
    change(offset, "EF");
    assertEquals(3, myStorage.getEvents().size());
    checkOrder();
  }
  
  @Test
  public void mergeWithPrevious() {
    change(1, "AB");
    assertEquals(1, myStorage.getEvents().size());
    
    int offset = myDocument.getLineStartOffset(1) + 2;
    change(offset, "CD");
    assertEquals(1, myStorage.getEvents().size());
    IncrementalCacheUpdateEvent event = myStorage.getEvents().get(0);
    assertEquals(0, event.getOldStartOffset());
    assertEquals(myDocument.getLineEndOffset(1), event.getOldEndOffset());
  }

  @Test
  public void mergeWithNext() {
    int offset = myDocument.getLineStartOffset(1) + 2;
    change(offset, "AB");
    assertEquals(1, myStorage.getEvents().size());
    
    change(1, "CD");
    assertEquals(1, myStorage.getEvents().size());
    IncrementalCacheUpdateEvent event = myStorage.getEvents().get(0);
    assertEquals(0, event.getOldStartOffset());
    assertEquals(myDocument.getLineEndOffset(1), event.getOldEndOffset());
  }
  
  @Test
  public void mergePreviousAndNext() {
    int offset = myDocument.getLineStartOffset(2) + 2;
    change(offset, "AB");
    assertEquals(1, myStorage.getEvents().size());
    
    change(3, "CD");
    assertEquals(2, myStorage.getEvents().size());
    checkOrder();

    offset = myDocument.getLineStartOffset(1) + 2;
    change(offset, "EF");
    assertEquals(1, myStorage.getEvents().size());
    IncrementalCacheUpdateEvent event = myStorage.getEvents().get(0);
    assertEquals(0, event.getOldStartOffset());
    assertEquals(myDocument.getLineEndOffset(2), event.getOldEndOffset());
  }
  
  @Test
  public void skipAddingNestedEvent() {
    int offset = myDocument.getLineStartOffset(2) + 2;
    change(offset, "1234567");
    assertEquals(1, myStorage.getEvents().size());
    
    IncrementalCacheUpdateEvent event = myStorage.getEvents().get(0);
    change(offset + 2, "ABC");
    assertEquals(1, myStorage.getEvents().size());
    assertSame(event, myStorage.getEvents().get(0));
  }
  
  private void checkOrder() {
    int startOffset = -1;
    for (IncrementalCacheUpdateEvent event : myStorage.getEvents()) {
      assertTrue(startOffset <= event.getOldStartOffset());
    }
  }
  
  private void change(int offset, String newText) {
    DocumentEventImpl event 
      = new DocumentEventImpl(myDocument, offset, myText.substring(offset, offset + newText.length()), newText, 1, false);
    myStorage.add(myDocument, new IncrementalCacheUpdateEvent(event));
  }
}
