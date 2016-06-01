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
package com.intellij.openapi.editor.actions;

import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class AddBlockInlayAction extends EditorAction {
  public AddBlockInlayAction() {
    super(new EditorActionHandler() {
      @Override
      protected void doExecute(Editor editor, @Nullable Caret caret, DataContext dataContext) {
        JEditorPane pane = new JEditorPane(UIUtil.HTML_MIME, "");
        pane.setEditorKit(UIUtil.getHTMLEditorKit(false));
        pane.setText("<html><head><base href=\"jar:file:///C:/Program Files/Java/jdk1.8.0_92/src.zip!/java/lang/String.java\">    <style type=\"text/css\">        #error {            background-color: #eeeeee;            margin-bottom: 10px;        }        p {            margin: 5px 0;        }    </style></head><body><small><b>java.lang</b></small><PRE>public final class <b>String</b>\n" +
                     "extends <a href=\"psi_element://java.lang.Object\"><code>Object</code></a>\n" +
                     "implements <a href=\"psi_element://java.io.Serializable\"><code>java.io.Serializable</code></a>,&nbsp;<a href=\"psi_element://java.lang.Comparable\"><code>Comparable</code></a>&lt;<a href=\"psi_element://java.lang.String\"><code>String</code></a>&gt;,&nbsp;<a href=\"psi_element://java.lang.CharSequence\"><code>CharSequence</code></a></PRE>\n" +
                     "   The <code>String</code> class represents character strings. All\n" +
                     "   string literals in Java programs, such as <code>&quot;abc&quot;</code>, are\n" +
                     "   implemented as instances of this class.\n" +
                     "   <p>\n" +
                     "   Strings are constant; their values cannot be changed after they\n" +
                     "   are created. String buffers support mutable strings.\n" +
                     "   Because String objects are immutable they can be shared. For example:\n" +
                     "   <blockquote><pre>\n" +
                     "       String str = \"abc\";\n" +
                     "   </pre></blockquote><p>\n" +
                     "   is equivalent to:\n" +
                     "   <blockquote><pre>\n" +
                     "       char data[] = {'a', 'b', 'c'};\n" +
                     "       String str = new String(data);\n" +
                     "   </pre></blockquote><p>\n" +
                     "   Here are some more examples of how strings can be used:\n" +
                     "   <blockquote><pre>\n" +
                     "       System.out.println(\"abc\");\n" +
                     "       String cde = \"cde\";\n" +
                     "       System.out.println(\"abc\" + cde);\n" +
                     "       String c = \"abc\".substring(2,3);\n" +
                     "       String d = cde.substring(1, 2);\n" +
                     "   </pre></blockquote>\n" +
                     "   <p>\n" +
                     "   The class <code>String</code> includes methods for examining\n" +
                     "   individual characters of the sequence, for comparing strings, for\n" +
                     "   searching strings, for extracting substrings, and for creating a\n" +
                     "   copy of a string with all characters translated to uppercase or to\n" +
                     "   lowercase. Case mapping is based on the Unicode Standard version\n" +
                     "   specified by the <a href=\"psi_element://java.lang.Character\"><code>Character</code></a> class.\n" +
                     "   <p>\n" +
                     "   The Java language provides special support for the string\n" +
                     "   concatenation operator (&nbsp;+&nbsp;), and for conversion of\n" +
                     "   other objects to strings. String concatenation is implemented\n" +
                     "   through the <code>StringBuilder</code>(or <code>StringBuffer</code>)\n" +
                     "   class and its <code>append</code> method.\n" +
                     "   String conversions are implemented through the method\n" +
                     "   <code>toString</code>, defined by <code>Object</code> and\n" +
                     "   inherited by all classes in Java. For additional information on\n" +
                     "   string concatenation and conversion, see Gosling, Joy, and Steele,\n" +
                     "   <i>The Java Language Specification</i>.\n" +
                     "  \n" +
                     "   <p> Unless otherwise noted, passing a <tt>null</tt> argument to a constructor\n" +
                     "   or method in this class will cause a <a href=\"psi_element://java.lang.NullPointerException\"><code>NullPointerException</code></a> to be\n" +
                     "   thrown.\n" +
                     "  \n" +
                     "   <p>A <code>String</code> represents a string in the UTF-16 format\n" +
                     "   in which <em>supplementary characters</em> are represented by <em>surrogate\n" +
                     "   pairs</em> (see the section <a href=\"psi_element://java.lang.Character###unicode\">Unicode\n" +
                     "   Character Representations</a> in the <code>Character</code> class for\n" +
                     "   more information).\n" +
                     "   Index values refer to <code>char</code> code units, so a supplementary\n" +
                     "   character uses two positions in a <code>String</code>.\n" +
                     "   <p>The <code>String</code> class provides methods for dealing with\n" +
                     "   Unicode code points (i.e., characters), in addition to those for\n" +
                     "   dealing with Unicode code units (i.e., <code>char</code> values).\n" +
                     "  \n" +
                     "   <DD><DL><DT><b>Since:</b><DD>JDK1.0</DD></DL></DD><DD><DL><DT><b>See Also:</b><DD><a href=\"psi_element://java.lang.Object#toString()\"><code>Object.toString()</code></a>,\n" +
                     "<a href=\"psi_element://java.lang.StringBuffer\"><code>StringBuffer</code></a>,\n" +
                     "<a href=\"psi_element://java.lang.StringBuilder\"><code>StringBuilder</code></a>,\n" +
                     "<a href=\"psi_element://java.nio.charset.Charset\"><code>Charset</code></a></DD></DL></DD></body></html>");
        pane.setBackground(HintUtil.INFORMATION_COLOR);
        pane.setBorder(BorderFactory.createLineBorder(Color.gray));
        int width = editor.getSettings().getRightMargin(editor.getProject()) * EditorUtil.getPlainSpaceWidth(editor);
        pane.setSize(width, Integer.MAX_VALUE);
        int height = pane.getPreferredSize().height;
        pane.setSize(width, height);
        editor.getInlayModel().addElement(editor.getCaretModel().getOffset(), Inlay.Type.BLOCK, new Inlay.Renderer() {
          @Override
          public void paint(@NotNull Graphics g, @NotNull Rectangle r, @NotNull Editor editor) {
            Graphics localG = g.create(r.x, r.y, r.width, r.height);
            pane.paint(localG);
            localG.dispose();
          }

          @Override
          public int calcHeightInPixels(@NotNull Editor editor) {
            return height;
          }

          @Override
          public int calcWidthInPixels(@NotNull Editor editor) {
            return width;
          }
        });
      }
    });
  }
}
