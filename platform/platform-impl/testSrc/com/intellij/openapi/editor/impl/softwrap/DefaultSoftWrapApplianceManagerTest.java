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
package com.intellij.openapi.editor.impl.softwrap;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ScrollingModel;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.util.text.StringUtil;
import org.jmock.Expectations;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;

import java.awt.*;
import java.util.Collections;

/**
 * @author Denis Zhdanov
 * @since 08/04/2010
 */
public class DefaultSoftWrapApplianceManagerTest {

  private static final String EDGE_MARKER            = "<EDGE>";
  private static final String WRAP_MARKER            = "<WRAP>";
  private static final int    SOFT_WRAP_DRAWING_SIZE = 11;

  private DefaultSoftWrapApplianceManager myManager;  
  private Mockery myMockery;
  private SoftWrapsStorage myStorage;
  private EditorEx myEditor;
  private SoftWrapPainter myPainter;
  private Document myDocument;
  private ScrollingModel myScrollingModel;
    
  @Before
  public void setUp() {
    myMockery = new JUnit4Mockery() {{
      setImposteriser(ClassImposteriser.INSTANCE);
    }};
    myStorage = myMockery.mock(SoftWrapsStorage.class);
    myEditor = myMockery.mock(EditorEx.class);
    myPainter = myMockery.mock(SoftWrapPainter.class);
    myDocument = myMockery.mock(Document.class);
    myScrollingModel = myMockery.mock(ScrollingModel.class);
    
    myMockery.checking(new Expectations() {{
      // Editor.
      allowing(myEditor).isViewer(); will(returnValue(false));

      // Document.
      allowing(myEditor).getDocument(); will(returnValue(myDocument));
      allowing(myDocument).addDocumentListener(with(any(DocumentListener.class)));
      allowing(myDocument).getLineNumber(with(any(int.class))); will(returnValue(0)); // Expecting to work only with single lines here.
      allowing(myDocument).getLineStartOffset(0); will(returnValue(0)); // Expecting to work only with single lines here.
      allowing(myDocument).isWritable(); will(returnValue(true));

      // Scrolling model.
      allowing(myEditor).getScrollingModel(); will(returnValue(myScrollingModel));

      // Storage.
      allowing(myStorage).removeAll();

      // Soft wrap drawings.
      allowing(myPainter).getMinDrawingWidth(with(any(SoftWrapDrawingType.class))); will(returnValue(SOFT_WRAP_DRAWING_SIZE));
    }});

    myManager = new DefaultSoftWrapApplianceManager(myStorage, myEditor, myPainter, new MockEditorTextRepresentationHelper());
  }
  
  @After
  public void checkExpectations() {
    myMockery.assertIsSatisfied();
  }

  @Test
  public void commaNotSeparated() {
    String document =
      "void method(String <WRAP>p1<EDGE>, String p2) {}";
    doTest(document);
  }

  @Test
  public void wrapOnExceedingWhiteSpace() {
    String document =
      "void method(String p1,<WRAP><EDGE> String p2) {}";
    doTest(document);
  }

  private void doTest(final String document) {
    final Context context = new Context(document);
    context.init();
    myMockery.checking(new Expectations() {{
      allowing(myScrollingModel).getVisibleArea(); will(returnValue(new Rectangle(0, 0, context.visualWidth, Integer.MAX_VALUE)));
      allowing(myDocument).getLineEndOffset(0); will(returnValue(context.document.length()));
    }});
    char[] documentChars = context.document.toCharArray();
    myManager.registerSoftWrapIfNecessary(documentChars, 0, documentChars.length, 0, Font.PLAIN);
  }

  private static TextChangeImpl createSoftWrap(int offset, int indent) {
    String text = "\n" + StringUtil.join(Collections.nCopies(indent, " "), "");
    return new TextChangeImpl(text, offset, offset);
  }

  /**
   * Utility class for parsing and initialising test data.
   * <p/>
   * <b>Note:</b> this class is line-oriented, i.e. it assumes that target document doesn't contain line feeds.
   */
  private class Context {

    private final StringBuilder buffer = new StringBuilder();
    private final String rawDocument;

    private String document;
    private int    visualWidth;
    private int    index;
    private int    wrapIndex;
    private int    edgeIndex;

    Context(String rawDocument) {
      if (rawDocument.contains("\n")) {
        throw new IllegalArgumentException(
          String.format("Don't expect to test multi-line documents but the one is detected: '%s'", rawDocument)
        );
      }
      this.rawDocument = rawDocument;
    }

    public void init() {
      wrapIndex = rawDocument.indexOf(WRAP_MARKER);
      edgeIndex = rawDocument.indexOf(EDGE_MARKER);
      while (wrapIndex >= 0 || edgeIndex >= 0) {
        if (wrapIndex >= 0 && edgeIndex >= 0) {
          if (wrapIndex < edgeIndex) {
            processWrap();
          }
          else {
            processEdge();
          }
          continue;
        }

        if (wrapIndex >= 0) {
          processWrap();
          continue;
        }

        if (edgeIndex >= 0) {
          processEdge();
          continue;
        }
        break;
      }
      buffer.append(rawDocument.substring(index));
      assert visualWidth > 0;
      document = buffer.toString();
    }

    private void processWrap() {
      buffer.append(rawDocument.substring(index, wrapIndex));
      myMockery.checking(new Expectations() {{
        one(myStorage).storeOrReplace(createSoftWrap(buffer.length(), 0));
      }});
      index = wrapIndex + WRAP_MARKER.length();
      wrapIndex = rawDocument.indexOf(WRAP_MARKER, index);
    }

    private void processEdge() {
      if (visualWidth > 0) {
        throw new IllegalArgumentException(String.format("More than one visual edge sign found at the document '%s'", rawDocument));
      }
      buffer.append(rawDocument.substring(index, edgeIndex));
      visualWidth = (buffer.length() * MockEditorTextRepresentationHelper.DEFAULT_SPACE_SIZE_IN_PIXELS) + SOFT_WRAP_DRAWING_SIZE + 1;
      index = edgeIndex + EDGE_MARKER.length();
      edgeIndex = rawDocument.indexOf(EDGE_MARKER, index);
    }
  }
}
