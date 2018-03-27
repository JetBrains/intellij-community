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
package com.intellij.openapi.editor.ex;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.LineExtensionInfo;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.impl.TextDrawingCallback;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.function.IntFunction;

public interface EditorEx extends Editor {
  @NonNls String PROP_INSERT_MODE = "insertMode";
  @NonNls String PROP_COLUMN_MODE = "columnMode";
  @NonNls String PROP_FONT_SIZE = "fontSize";
  Key<TextRange> LAST_PASTED_REGION = Key.create("LAST_PASTED_REGION");

  @NotNull
  @Override
  DocumentEx getDocument();

  @Override
  @NotNull
  MarkupModelEx getMarkupModel();

  /**
   * Returns the markup model for the underlying Document.
   * <p>
   * This model differs from the one from DocumentMarkupModel#forDocument,
   * as it does not contain highlighters that should not be visible in this Editor.
   * (for example, debugger breakpoints in a diff viewer editors)
   *
   * @return the markup model instance.
   * @see com.intellij.openapi.editor.markup.MarkupEditorFilter
   * @see com.intellij.openapi.editor.impl.EditorImpl#setHighlightingFilter(Condition)
   * @see com.intellij.openapi.editor.impl.DocumentMarkupModel#forDocument(Document, Project, boolean)
   */
  @NotNull
  MarkupModelEx getFilteredDocumentMarkupModel();

  @NotNull
  EditorGutterComponentEx getGutterComponentEx();

  @NotNull
  EditorHighlighter getHighlighter();

  JComponent getPermanentHeaderComponent();

  /**
   * shouldn't be called during Document update
   */
  void setViewer(boolean isViewer);

  void setPermanentHeaderComponent(JComponent component);

  void setHighlighter(@NotNull EditorHighlighter highlighter);

  void setColorsScheme(@NotNull EditorColorsScheme scheme);

  void setInsertMode(boolean val);

  void setColumnMode(boolean val);

  int VERTICAL_SCROLLBAR_LEFT = 0;
  int VERTICAL_SCROLLBAR_RIGHT = 1;

  void setVerticalScrollbarOrientation(@MagicConstant(intValues = {VERTICAL_SCROLLBAR_LEFT, VERTICAL_SCROLLBAR_RIGHT}) int type);

  @MagicConstant(intValues = {VERTICAL_SCROLLBAR_LEFT, VERTICAL_SCROLLBAR_RIGHT})
  int getVerticalScrollbarOrientation();

  void setVerticalScrollbarVisible(boolean b);

  void setHorizontalScrollbarVisible(boolean b);

  CutProvider getCutProvider();

  CopyProvider getCopyProvider();

  PasteProvider getPasteProvider();

  DeleteProvider getDeleteProvider();

  void repaint(int startOffset, int endOffset);

  void reinitSettings();

  void addPropertyChangeListener(@NotNull PropertyChangeListener listener, @NotNull Disposable parentDisposable);
  void addPropertyChangeListener(@NotNull PropertyChangeListener listener);

  void removePropertyChangeListener(@NotNull PropertyChangeListener listener);

  int getMaxWidthInRange(int startOffset, int endOffset);

  boolean setCaretVisible(boolean b);

  boolean setCaretEnabled(boolean enabled);

  void addFocusListener(@NotNull FocusChangeListener listener);

  void addFocusListener(@NotNull FocusChangeListener listener, @NotNull Disposable parentDisposable);

  void setOneLineMode(boolean b);

  @NotNull
  JScrollPane getScrollPane();

  boolean isRendererMode();

  void setRendererMode(boolean isRendererMode);

  void setFile(VirtualFile vFile);

  @NotNull
  DataContext getDataContext();

  boolean processKeyTyped(@NotNull KeyEvent e);

  void setFontSize(int fontSize);

  @NotNull
  Color getBackgroundColor();

  void setBackgroundColor(Color color);

  Dimension getContentSize();

  boolean isEmbeddedIntoDialogWrapper();
  void setEmbeddedIntoDialogWrapper(boolean b);

  VirtualFile getVirtualFile();

  /**
   * @deprecated Use {@link #offsetToLogicalPosition(int)}
   * or {@link EditorUtil#calcColumnNumber(Editor, CharSequence, int, int, int)} instead. To be removed in IDEA 2017.2.
   */
  int calcColumnNumber(@NotNull CharSequence text, int start, int offset, int tabSize);

  /**
   * @deprecated Use {@link #offsetToLogicalPosition(int)}
   * or {@link EditorUtil#calcColumnNumber(Editor, CharSequence, int, int, int)} instead. To be removed in IDEA 2017.2.
   */
  int calcColumnNumber(int offset, int lineIndex);

  TextDrawingCallback getTextDrawingCallback();

  @NotNull
  @Override
  FoldingModelEx getFoldingModel();

  @NotNull
  @Override
  SoftWrapModelEx getSoftWrapModel();

  @NotNull
  @Override
  ScrollingModelEx getScrollingModel();

  /**
   * Creates color scheme delegate which is bound to current editor. E.g. all schema changes will update editor state.
   */
  @NotNull
  EditorColorsScheme createBoundColorSchemeDelegate(@Nullable EditorColorsScheme customGlobalScheme);

  /**
   * Instructs current editor about soft wraps appliance appliance use-case.
   * <p/>
   * {@link SoftWrapAppliancePlaces#MAIN_EDITOR} is used by default.
   *
   * @param place   soft wraps appliance appliance use-case
   * @deprecated set {@link EditorKind} via
   * {@link com.intellij.openapi.editor.EditorFactory#createEditor(Document, Project, EditorKind) },
   * {@link com.intellij.openapi.editor.EditorFactory#createViewer(Document, Project, EditorKind)}
   * {@link com.intellij.openapi.editor.EditorFactory#createEditor(Document, Project, VirtualFile, boolean, EditorKind)}
   */
  @Deprecated
  void setSoftWrapAppliancePlace(@NotNull SoftWrapAppliancePlaces place);

  /**
   * Allows to define {@code 'placeholder text'} for the current editor, i.e. virtual text that will be represented until
   * any user data is entered.
   *
   * Feel free to see the detailed feature
   * definition <a href="http://dev.w3.org/html5/spec/Overview.html#the-placeholder-attribute">here</a>.
   *
   * @param text    virtual text to show until user data is entered or the editor is focused
   */
  void setPlaceholder(@Nullable CharSequence text);

  /**
   * Sets text attributes for a placeholder. Font style and color are currently supported. 
   * {@code null} means default values should be used.
   * 
   * @see #setPlaceholder(CharSequence)
   */
  void setPlaceholderAttributes(@Nullable TextAttributes attributes);
  
  /**
   * Controls whether {@code 'placeholder text'} is visible when editor is focused.
   *
   * @param show   flag indicating whether placeholder is visible when editor is focused.
   *
   * @see EditorEx#setPlaceholder(CharSequence)
   */
  void setShowPlaceholderWhenFocused(boolean show);

  /**
   * Allows to answer if 'sticky selection' is active for the current editor.
   * <p/>
   * 'Sticky selection' means that every time caret position changes, selection end offset is automatically set to the same position.
   * Selection start is always caret offset on {@link #setStickySelection(boolean)} call with {@code 'true'} argument.
   *
   * @return      {@code true} if 'sticky selection' mode is active at the current editor; {@code false} otherwise
   */
  boolean isStickySelection();

  /**
   * Allows to set current {@link #isStickySelection() sticky selection} mode.
   *
   * @param enable      flag that identifies if {@code 'sticky selection'} mode should be enabled
   */
  void setStickySelection(boolean enable);

  /**
   * @return  width in pixels of the {@link #setPrefixTextAndAttributes(String, TextAttributes) prefix} used with the current editor if any;
   *          zero otherwise
   */
  int getPrefixTextWidthInPixels();

  /**
   * Allows to define prefix to be displayed on every editor line and text attributes to use for its coloring.
   *
   * @param prefixText  target prefix text
   * @param attributes  text attributes to use during given prefix painting
   */
  void setPrefixTextAndAttributes(@Nullable String prefixText, @Nullable TextAttributes attributes);

  /**
   * @return    current 'pure painting mode' status
   * @see #setPurePaintingMode(boolean)
   */
  boolean isPurePaintingMode();

  /**
   * We often re-use the logic encapsulated at the editor. For example, every time we show editor fragment (folding, preview etc) we
   * create a dedicated graphics object and ask the editor to paint into it.
   * <p>
   * The thing is that the editor itself may change its state if any postponed operation is triggered by the painting request
   * (e.g. soft wraps recalculation is triggered by the paint request and newly calculated soft wraps cause caret to change its position).
   * <p>
   * This method allows to inform the editor that all subsequent painting request should not change the editor state.
   * <p>
   * In 'pure painting mode' editor also behaves as if soft wraps were not enabled.
   *
   * @param enabled  'pure painting mode' status to use
   */
  void setPurePaintingMode(boolean enabled);

  /**
   * Registers a function which will be applied to a line number to obtain additional text fragments. The fragments returned by the
   * function will be drawn in the editor after end of the line (together with fragments returned by {@link com.intellij.openapi.editor.EditorLinePainter} extensions).
   */
  void registerLineExtensionPainter(IntFunction<Collection<LineExtensionInfo>> lineExtensionPainter);

  /**
   * Allows to register a callback that will be called one each repaint of the editor vertical scrollbar.
   * This is needed to allow a parent component draw above the scrollbar components (e.g. in the merge tool),
   * otherwise the drawings are cleared once the scrollbar gets repainted (which may happen suddenly, because the scrollbar UI uses the
   * {@link com.intellij.util.ui.Animator} to draw itself.
   * @param callback  callback which will be called from the {@link JComponent#paint(Graphics)} method of
   *                  the editor vertical scrollbar.
   */
  void registerScrollBarRepaintCallback(@Nullable ButtonlessScrollBarUI.ScrollbarRepaintCallback callback);

  /**
   * @return the offset that the caret is expected to be but maybe not yet.
   * E.g. when user right-clicks the mouse the caret is not immediately jumps there but the click-handler wants to know that location already.
   *
   * When no mouse-clicks happened return the regular caret offset.
   */
  int getExpectedCaretOffset();

  /**
   * Sets id of action group what will be used to construct context menu displayed on mouse right button's click. Setting this to 
   * {@code null} disables built-in logic for showing context menu (it can still be achieved by implementing corresponding mouse
   * event listener).
   * 
   * @see #getContextMenuGroupId() 
   */
  void setContextMenuGroupId(@Nullable String groupId);

  /**
   * Returns id of action group what will be used to construct context menu displayed on mouse right button's click. {@code null}
   * value means built-in logic for showing context menu is disabled.
   * 
   * @see #setContextMenuGroupId(String)
   */
  @Nullable
  String getContextMenuGroupId();
}
