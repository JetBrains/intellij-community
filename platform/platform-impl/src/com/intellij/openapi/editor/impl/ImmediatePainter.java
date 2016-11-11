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
package com.intellij.openapi.editor.impl;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actions.*;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.util.EditorUIUtil;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Pavel Fatin
 */
public class ImmediatePainter {
  // Characters that excluded from zero-latency painting after key typing
  private static final Set<Character> KEY_CHARS_TO_SKIP =
    new HashSet<>(Arrays.asList('\n', '\t', '(', ')', '[', ']', '{', '}', '"', '\''));

  // Characters that excluded from zero-latency painting after document update
  private static final Set<Character> DOCUMENT_CHARS_TO_SKIP =
    new HashSet<>(Arrays.asList(')', ']', '}', '"', '\''));

  // Although it's possible to paint arbitrary line changes immediately,
  // our primary interest is direct user editing actions, where visual delay is crucial.
  // Moreover, as many subsystems (like PsiToDocumentSynchronizer, UndoManager, etc.) don't enforce bulk document updates,
  // and can trigger multiple write actions / document changes sequentially, we need to avoid possible flickering during such an activity.
  // There seems to be no other way to determine whether particular document change is triggered by direct user editing
  // (raw character typing is handled separately, even before write action).
  private static final Set<Class> IMMEDIATE_EDITING_ACTIONS = new HashSet<>(Arrays.asList(BackspaceAction.class,
                                                                                          DeleteAction.class,
                                                                                          DeleteToWordStartAction.class,
                                                                                          DeleteToWordEndAction.class,
                                                                                          DeleteToWordStartInDifferentHumpsModeAction.class,
                                                                                          DeleteToWordEndInDifferentHumpsModeAction.class,
                                                                                          DeleteToLineStartAction.class,
                                                                                          DeleteToLineEndAction.class,
                                                                                          CutAction.class,
                                                                                          PasteAction.class));
  public static final String ZERO_LATENCY_TYPING_KEY = "editor.zero.latency.typing";

  public static final String ZERO_LATENCY_TYPING_DEBUG_KEY = "editor.zero.latency.typing.debug";

  public static final int DEBUG_PAUSE_DURATION = 1000;

  // TODO Should be removed when IDEA adopts typing without starting write actions.
  private static final boolean VIM_PLUGIN_LOADED = isPluginLoaded("IdeaVIM");

  private Rectangle myOldArea = new Rectangle(0, 0, 0, 0);
  private Rectangle myOldTailArea = new Rectangle(0, 0, 0, 0);
  private boolean myImmediateEditingInProgress;

  private final EditorImpl myEditor;


  ImmediatePainter(EditorImpl editor) {
    myEditor = editor;
    AnActionListener.Adapter actionListener = new AnActionListener.Adapter() {
      @Override
      public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        if (isZeroLatencyTypingEnabled() && IMMEDIATE_EDITING_ACTIONS.contains(action.getClass()) &&
            !(action.getClass() == PasteAction.class && getSelectionModel().hasSelection())) {
          myImmediateEditingInProgress = true;
        }
      }

      @Override
      public void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
        if (isZeroLatencyTypingEnabled()) {
          myImmediateEditingInProgress = false;
        }
      }
    };

    ActionManager.getInstance().addAnActionListener(actionListener, editor.getDisposable());
  }

  protected Document getDocument() {
    return myEditor.getDocument();
  }

  protected SelectionModel getSelectionModel() {
    return myEditor.getSelectionModel();
  }

  protected EditorHighlighter getHighlighter() {
    return myEditor.getHighlighter();
  }

  protected CaretModel getCaretModel() {
    return myEditor.getCaretModel();
  }

  protected EditorColorsScheme getColorsScheme() {
    return myEditor.getColorsScheme();
  }

  protected EditorComponentImpl getContentComponent() {
    return myEditor.getContentComponent();
  }

  public void paintCharacter(Graphics g, char c) {
    if (isZeroLatencyTypingEnabled() && getDocument().isWritable() && !myEditor.isViewer() && canPaintImmediately(c)) {
      for (Caret caret : getCaretModel().getAllCarets()) {
        paintImmediately(g, caret.getOffset(), c, myEditor.isInsertMode());
      }
    }
  }

  public void beforeUpdate(@NotNull DocumentEvent e) {
    if (isZeroLatencyTypingEnabled() && myImmediateEditingInProgress && canPaintImmediately(e)) {
      int offset = e.getOffset();
      int length = e.getOldLength();

      myOldArea = lineRectangleBetween(offset, offset + length);

      myOldTailArea = lineRectangleBetween(offset + length, getDocument().getLineEndOffset(getDocument().getLineNumber(offset)));
      if (myOldTailArea.isEmpty()) {
        myOldTailArea.width += EditorUtil.getSpaceWidth(Font.PLAIN, myEditor); // include possible caret
      }
    }
  }

  public void paintUpdate(Graphics g, @NotNull DocumentEvent e) {
    if (isZeroLatencyTypingEnabled() && myImmediateEditingInProgress && canPaintImmediately(e)) {
      paintImmediately(g, e);
    }
  }

  public static boolean isZeroLatencyTypingEnabled() {
    // Zero-latency typing is suppressed when Idea VIM plugin is loaded, because of VIM-1007.
    // That issue will be resolved automatically when IDEA adopts typing without starting write actions.
    return !VIM_PLUGIN_LOADED && Registry.is(ZERO_LATENCY_TYPING_KEY);
  }

  private static boolean isZeroLatencyTypingDebugEnabled() {
    return Registry.is(ZERO_LATENCY_TYPING_DEBUG_KEY);
  }

  private static boolean isPluginLoaded(@NotNull String id) {
    PluginId pluginId = PluginId.findId(id);
    if (pluginId == null) return false;
    IdeaPluginDescriptor plugin = PluginManager.getPlugin(pluginId);
    if (plugin == null) return false;
    return plugin.isEnabled();
  }

  private boolean canPaintImmediately(char c) {
    return getDocument() instanceof DocumentImpl &&
           getHighlighter() instanceof LexerEditorHighlighter &&
           !getSelectionModel().hasSelection() &&
           arePositionsWithinDocument(getCaretModel().getAllCarets()) &&
           areVisualLinesUnique(getCaretModel().getAllCarets()) &&
           !isInplaceRenamerActive() &&
           !KEY_CHARS_TO_SKIP.contains(c);
  }

  private static boolean areVisualLinesUnique(java.util.List<Caret> carets) {
    if (carets.size() > 1) {
      TIntHashSet lines = new TIntHashSet(carets.size());
      for (Caret caret : carets) {
        if (!lines.add(caret.getVisualLineStart())) {
          return false;
        }
      }
    }
    return true;
  }

  // Checks whether all the carets are within document or some of them are in a so called "virtual space".
  private boolean arePositionsWithinDocument(java.util.List<Caret> carets) {
    for (Caret caret : carets) {
      if (caret.getLogicalPosition().compareTo(myEditor.offsetToLogicalPosition(caret.getOffset())) != 0) {
        return false;
      }
    }
    return true;
  }

  // TODO Improve the approach - handle such cases in a more general way.
  private boolean isInplaceRenamerActive() {
    Key<?> key = Key.findKeyByName("EditorInplaceRenamer");
    return key != null && key.isIn(myEditor);
  }

  // Called to display a single character insertion before starting a write action and the general painting routine.
  // Bypasses RepaintManager (c.repaint, c.paintComponent) and double buffering (g.paintImmediately) to minimize visual lag.
  // TODO Should be replaced with the generic paintImmediately(event) call when we implement typing without starting write actions.
  private void paintImmediately(Graphics g, int offset, char c, boolean insert) {
    if (g == null) return; // editor component is currently not displayable

    TextAttributes attributes = ((LexerEditorHighlighter)getHighlighter()).getAttributesForTypedChar(getDocument(), offset, c);

    int fontType = attributes.getFontType();
    FontInfo fontInfo = EditorUtil.fontForChar(c, attributes.getFontType(), myEditor);
    Font font = fontInfo.getFont();

    // it's more reliable to query actual font metrics
    FontMetrics fontMetrics = myEditor.getFontMetrics(fontType);

    int charWidth = fontMetrics.charWidth(c);

    int delta = charWidth;

    if (!insert && offset < getDocument().getTextLength()) {
      delta -= fontMetrics.charWidth(getDocument().getCharsSequence().charAt(offset));
    }

    Rectangle tailArea = lineRectangleBetween(offset, getDocument().getLineEndOffset(myEditor.offsetToLogicalLine(offset)));
    if (tailArea.isEmpty()) {
      tailArea.width += EditorUtil.getSpaceWidth(fontType, myEditor); // include caret
    }

    Color background = attributes.getBackgroundColor() == null ? getCaretRowBackground() : attributes.getBackgroundColor();

    Rectangle newArea = lineRectangleBetween(offset, offset);
    newArea.width += charWidth;

    String newText = Character.toString(c);
    Point point = newArea.getLocation();
    int ascent = myEditor.getAscent();
    Color foreground = attributes.getForegroundColor() == null ? myEditor.getForegroundColor() : attributes.getForegroundColor();

    EditorUIUtil.setupAntialiasing(g);

    // pre-compute all the arguments beforehand to minimize delays between the calls (as there's no double-buffering)
    if (delta != 0) {
      shift(g, tailArea, delta);
    }
    fill(g, newArea, background);
    print(g, newText, point, ascent, font, foreground);

    // flush changes (there can be batching / buffering in video driver)
    Toolkit.getDefaultToolkit().sync();

    if (isZeroLatencyTypingDebugEnabled()) {
      pause();
    }
  }

  private static void pause() {
    try {
      Thread.sleep(DEBUG_PAUSE_DURATION);
    }
    catch (InterruptedException e) {
      // ...
    }
  }

  private boolean canPaintImmediately(@NotNull DocumentEvent e) {
    return getDocument() instanceof DocumentImpl &&
           !isInplaceRenamerActive() &&
           StringUtil.indexOf(e.getOldFragment(), '\n') == -1 &&
           StringUtil.indexOf(e.getNewFragment(), '\n') == -1 &&
           !(e.getNewLength() == 1 && DOCUMENT_CHARS_TO_SKIP.contains(e.getNewFragment().charAt(0)));
  }

  // Called to display insertion / deletion / replacement within a single line before the general painting routine.
  // Bypasses RepaintManager (c.repaint, c.paintComponent) and double buffering (g.paintImmediately) to minimize visual lag.
  private void paintImmediately(Graphics g, @NotNull DocumentEvent e) {
    if (g == null) return; // editor component is currently not displayable

    int offset = e.getOffset();
    String newText = e.getNewFragment().toString();
    Rectangle newArea = lineRectangleBetween(offset, offset + newText.length());
    int delta = newArea.width - myOldArea.width;
    Color background = getCaretRowBackground();

    if (delta != 0) {
      if (delta < 0) {
        // Pre-paint carets at new positions, if needed, before shifting the tail area (to avoid flickering),
        // because painting takes some time while copyArea is almost instantaneous.
        EditorImpl.CaretRectangle[] caretRectangles = myEditor.getCaretCursor().getCaretLocations(true);
        if (caretRectangles != null) {
          for (EditorImpl.CaretRectangle it : caretRectangles) {
            Rectangle r = toRectangle(it);
            if (myOldArea.contains(r) && !newArea.contains(r)) {
              myEditor.getCaretCursor().paintAt(g, it.myPoint.x - delta, it.myPoint.y, it.myWidth, it.myCaret);
            }
          }
        }
      }

      shift(g, myOldTailArea, delta);

      if (delta < 0) {
        Rectangle remainingArea = new Rectangle(myOldTailArea.x + myOldTailArea.width + delta,
                                                myOldTailArea.y, -delta, myOldTailArea.height);
        fill(g, remainingArea, background);
      }
    }

    if (!newArea.isEmpty()) {
      TextAttributes attributes = getHighlighter().createIterator(offset).getTextAttributes();

      Point point = newArea.getLocation();
      int ascent = myEditor.getAscent();
      // simplified font selection (based on the first character)
      FontInfo fontInfo = EditorUtil.fontForChar(newText.charAt(0), attributes.getFontType(), myEditor);
      Font font = fontInfo.getFont();

      Color foreground = attributes.getForegroundColor() == null ? myEditor.getForegroundColor() : attributes.getForegroundColor();

      EditorUIUtil.setupAntialiasing(g);

      // pre-compute all the arguments beforehand to minimize delay between the calls (as there's no double-buffering)
      fill(g, newArea, background);
      print(g, newText, point, ascent, font, foreground);
    }

    // flush changes (there can be batching / buffering in video driver)
    Toolkit.getDefaultToolkit().sync();

    if (isZeroLatencyTypingDebugEnabled()) {
      pause();
    }
  }

  @NotNull
  private Rectangle lineRectangleBetween(int begin, int end) {
    Point p1 = myEditor.offsetToXY(begin, false);
    Point p2 = myEditor.offsetToXY(end, false);
    // When soft wrap is present, handle only the first visual line (for simplicity, yet it works reasonably well)
    int x2 = p1.y == p2.y ? p2.x : Math.max(p1.x, getContentComponent().getWidth() - myEditor.getVerticalScrollBar().getWidth());
    return new Rectangle(p1.x, p1.y, x2 - p1.x, myEditor.getLineHeight());
  }

  @NotNull
  private Rectangle toRectangle(@NotNull EditorImpl.CaretRectangle caretRectangle) {
    Point p = caretRectangle.myPoint;
    return new Rectangle(p.x, p.y, caretRectangle.myWidth, myEditor.getLineHeight());
  }

  @NotNull
  private Color getCaretRowBackground() {
    Color color = getColorsScheme().getColor(EditorColors.CARET_ROW_COLOR);
    return color == null ? myEditor.getBackgroundColor() : color;
  }

  private static void shift(@NotNull Graphics g, @NotNull Rectangle r, int delta) {
    g.copyArea(r.x, r.y, r.width, r.height, delta, 0);
  }

  private static void fill(@NotNull Graphics g, @NotNull Rectangle r, @NotNull Color color) {
    g.setColor(color);
    g.fillRect(r.x, r.y, r.width, r.height);
  }

  private static void print(@NotNull Graphics g, @NotNull String text, @NotNull Point point,
                            int ascent, @NotNull Font font, @NotNull Color color) {
    g.setFont(font);
    g.setColor(color);
    g.drawString(text, point.x, point.y + ascent);
  }
}
