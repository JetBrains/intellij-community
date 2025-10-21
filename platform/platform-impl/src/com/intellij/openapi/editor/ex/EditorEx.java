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
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.event.EditorMouseListener;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.impl.TextDrawingCallback;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.ButtonlessScrollBarUI;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.*;

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
  @NonNls String PROP_FONT_SIZE_2D = "fontSize2D";
  @NonNls String PROP_ONE_LINE_MODE = "oneLineMode";
  @NonNls String PROP_HIGHLIGHTER = "highlighter";
  @NonNls String PROP_HEADER_COMPONENT = "headerComponent";

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
   * @see com.intellij.openapi.editor.impl.EditorImpl#setHighlightingPredicate(java.util.function.Predicate)
   * @see com.intellij.openapi.editor.impl.DocumentMarkupModel#forDocument(Document, Project, boolean)
   */
  @NotNull
  MarkupModelEx getFilteredDocumentMarkupModel();

  @NotNull
  EditorGutterComponentEx getGutterComponentEx();

  JComponent getPermanentHeaderComponent();

  /**
   * shouldn't be called during Document update
   */
  void setViewer(boolean isViewer);

  void setHighlighter(@NotNull EditorHighlighter highlighter);

  void setPermanentHeaderComponent(JComponent component);

  /**
   * This method doesn't set the scheme itself, but the delegate
   * @param scheme - original scheme
   * @see com.intellij.openapi.editor.impl.EditorColorSchemeDelegate
   */
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

  @NotNull
  CutProvider getCutProvider();

  @NotNull
  CopyProvider getCopyProvider();

  @NotNull
  PasteProvider getPasteProvider();

  @NotNull
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

  void setFile(@NotNull VirtualFile vFile);

  @NotNull
  DataContext getDataContext();

  boolean processKeyTyped(@NotNull KeyEvent e);

  void setFontSize(int fontSize);

  default void setFontSize(float fontSize) {
    setFontSize((int)(fontSize + 0.5));
  }

  @NotNull
  Color getBackgroundColor();

  void setBackgroundColor(Color color);

  Dimension getContentSize();

  boolean isEmbeddedIntoDialogWrapper();
  void setEmbeddedIntoDialogWrapper(boolean b);

  @Override
  @Nullable
  @ApiStatus.Obsolete
  VirtualFile getVirtualFile();

  @NotNull
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
   * Allows to define {@code 'placeholder text'} for the current editor, i.e. virtual text that will be represented until
   * any user data is entered.
   * Feel free to see the detailed feature
   * definition <a href="http://dev.w3.org/html5/spec/Overview.html#the-placeholder-attribute">here</a>.
   *
   * @param text    virtual text to show until user data is entered or the editor is focused
   */
  void setPlaceholder(@Nullable @Nls CharSequence text);

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
   * function will be drawn in the editor after end of the line (together with fragments returned by {@link EditorLinePainter} extensions).
   */
  void registerLineExtensionPainter(@NotNull IntFunction<? extends @NotNull Collection<? extends LineExtensionInfo>> lineExtensionPainter);

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
   * @return the offset that the caret is expected to be but maybe not yet. This can be used in
   * {@link EditorMouseListener#mousePressed(EditorMouseEvent)} implementation, as at the time this method is invoked caret wasn't yet moved
   * to the press position. In other circumstances it's just equal to primary caret's offset.
   */
  int getExpectedCaretOffset();

  /**
   * Sets id of action group what will be used to construct context menu displayed by default editor popup handler on mouse right button's
   * click (with {@code null} value disabling context menu). This method might have no effect if default editor's popup handler was
   * overridden using {@link #installPopupHandler(EditorPopupHandler)}.
   * 
   * @see #getContextMenuGroupId()
   */
  void setContextMenuGroupId(@Nullable String groupId);

  /**
   * Returns id of action group what will be used to construct context menu displayed by default editor popup handler on mouse right
   * button's click ({@code null} value meaning no context menu). Returned value might be meaningless if default editor's popup handler
   * was overridden using {@link #installPopupHandler(EditorPopupHandler)}.
   * 
   * @see #setContextMenuGroupId(String)
   */
  @Nullable
  String getContextMenuGroupId();

  /**
   * Allows to override default editor's context popup logic.
   * <p>
   * Default handler shows a context menu corresponding to a certain action group
   * registered in {@link ActionManager}. Group's id can be changed using {@link #setContextMenuGroupId(String)}. For inline custom visual
   * elements (inlays) action group is determined by {@link EditorCustomElementRenderer#getContextMenuGroupId(Inlay)} and
   * {@link EditorCustomElementRenderer#getContextMenuGroup(Inlay)}.
   * <p>
   * If multiple handlers are installed, they are processed in order, starting from the most recently installed one. Processing stops when
   * some handler returns {@code true} from {@link EditorPopupHandler#handlePopup(EditorMouseEvent)} method.
   *
   * @see #uninstallPopupHandler(EditorPopupHandler)
   */
  void installPopupHandler(@NotNull EditorPopupHandler popupHandler);

  /**
   * Removes previously installed {@link EditorPopupHandler}.
   *
   * @see #installPopupHandler(EditorPopupHandler)
   */
  void uninstallPopupHandler(@NotNull EditorPopupHandler popupHandler);

  default @Nullable ActionGroup getPopupActionGroup(@NotNull EditorMouseEvent event) {
    return null;
  }

  /**
   * If {@code cursor} parameter value is not {@code null}, sets custom cursor to {@link #getContentComponent() editor's content component},
   * otherwise restores default editor cursor management logic ({@code requestor} parameter value should be the same in both setting and
   * restoring requests). 'Restoring' call for a requestor, which hasn't set a cursor previously, has no effect. If multiple requestors have
   * currently set custom cursors, one of them will be used (it is unspecified, which one).
   */
  void setCustomCursor(@NotNull Object requestor, @Nullable Cursor cursor);

  /**
   * Returns the current height of the sticky lines panel component in pixels.
   * <p>
   * The integer value is in the range from {@code 0} to {@code lineHeight * stickyLinesLimit}.
   * It is zero if the sticky lines feature is disabled or the panel is empty.
   * <p>
   * NOTE: the value is not necessarily a multiple of line height.
   * For example, it can be {@code lineHeight / 2} if the editor is scrolled that way
   * to render only bottom half of a sticky line.
   */
  @ApiStatus.Experimental
  default int getStickyLinesPanelHeight() {
    return 0;
  }
}
