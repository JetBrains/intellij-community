// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.codeInsight.hints.presentation.InputHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCustomElementRenderer;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.BitUtil;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XLineBreakpointType;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

class InlineBreakpointInlayRenderer implements EditorCustomElementRenderer, InputHandler {
  // There could be three states:
  // * not-null breakpoint and not-null variant -- we have a breakpoint and a matching variant (normal case)
  // * null breakpoint and not-null variant -- we have a variant where breakpoint could be set (normal case)
  // * not-null breakpoint and null variant -- we have a breakpoint but no matching variant (outdated breakpoint?)
  private final @Nullable XLineBreakpointImpl<?> breakpoint;
  private final @Nullable XLineBreakpointType<?>.XLineBreakpointVariant variant;

  // EditorCustomElementRenderer's methods have inlay as parameter,
  // but InputHandler's methods do not have it.
  private Inlay<InlineBreakpointInlayRenderer> inlay;

  boolean hovered;

  InlineBreakpointInlayRenderer(@Nullable XLineBreakpointImpl<?> breakpoint,
                                @Nullable XLineBreakpointType<?>.XLineBreakpointVariant variant) {
    if (breakpoint == null && variant == null) {
      throw new IllegalArgumentException();
    }
    this.breakpoint = breakpoint;
    this.variant = variant;
  }

  void setInlay(Inlay<InlineBreakpointInlayRenderer> inlay) {
    this.inlay = inlay;
  }

  private float scale() {
    return 0.75f // FIXME[inline-bp]: introduce option to make inline icons slightly smaller than gutter ones
           * editorScale();
  }

  private float editorScale() {
    var editor = inlay.getEditor();
    return (inlay.getEditor() instanceof EditorImpl) ? ((EditorImpl)editor).getScale() : 1;
  }

  @NotNull
  private static FontMetrics getEditorFontMetrics(@NotNull Editor editor) {
    var colorsScheme = editor.getColorsScheme();
    var font = UIUtil.getFontWithFallback(colorsScheme.getFont(EditorFontType.PLAIN));
    var context = FontInfo.getFontRenderContext(editor.getContentComponent());
    return FontInfo.getFontMetrics(font, context);
  }

  @Override
  public int calcWidthInPixels(@NotNull Inlay inlay) {
    var fontMetrics = getEditorFontMetrics(inlay.getEditor());
    var twoChars = "nn"; // Use two average width characters (might be important for non-monospaced fonts).
    return fontMetrics.stringWidth(twoChars);
  }

  @Override
  public void paint(@NotNull Inlay inlay,
                    @NotNull Graphics g,
                    @NotNull Rectangle targetRegion,
                    @NotNull TextAttributes textAttributes) {
    JComponent component = inlay.getEditor().getComponent();

    Icon baseIcon;
    float alpha;
    if (breakpoint != null) {
      baseIcon = breakpoint.getIcon();
      alpha = 1;
    }
    else {
      assert variant != null;
      baseIcon = variant.getType().getEnabledIcon();

      // FIXME[inline-bp]: do we need to rename the property?
      alpha = JBUI.getFloat("Breakpoint.iconHoverAlpha", 0.5f);
      alpha = Math.max(0, Math.min(alpha, 1));
      if (hovered) {
        // Slightly increase visibility (e.g. 0.5 -> 0.625).
        // FIXME[inline-bp]: ask Yulia Zozulya if we really need it?
        alpha = (3 * alpha + 1) / 4;
      }
    }

    // FIXME[inline-bp]: limit icon size to region size with some padding
    Icon scaledIcon = IconUtil.scale(baseIcon, component, scale());

    // Draw icon in the center of the region.
    var x = targetRegion.x + targetRegion.width / 2 - scaledIcon.getIconWidth() / 2;
    var y = targetRegion.y + targetRegion.height / 2 - scaledIcon.getIconHeight() / 2;
    GraphicsUtil.paintWithAlpha(g, alpha, () -> scaledIcon.paintIcon(component, g, x, y));
  }

  @Override
  public void mousePressed(@NotNull MouseEvent event, @NotNull Point translated) {
    invokePopupIfNeeded(event);
  }

  @Override
  public void mouseReleased(@NotNull MouseEvent event, @NotNull Point translated) {
    invokePopupIfNeeded(event);
  }

  private void invokePopupIfNeeded(@NotNull MouseEvent event) {
    if (event.isPopupTrigger()) {
      if (breakpoint != null) {
        var bounds = inlay.getBounds();
        if (bounds == null) return;
        Point center = new Point((int)bounds.getCenterX(), (int)bounds.getCenterY());
        DebuggerUIUtil.showXBreakpointEditorBalloon(breakpoint.getProject(), center, inlay.getEditor().getContentComponent(), false,
                                                    breakpoint);
      }
      else {
        // FIXME[inline-bp]: show context like in gutter (XDebugger.Hover.Breakpoint.Context.Menu),
        //                   but actions should be adapted for inline breakpoints.
      }
      event.consume();
    }
  }

  private enum ClickAction {
    SET, ENABLE_DISABLE, REMOVE
  }

  @Override
  public void mouseClicked(@NotNull MouseEvent event, @NotNull Point translated) {
    event.consume();

    var button = event.getButton();
    ClickAction action = null;
    if (breakpoint != null) {
      // mimic gutter icon
      if (button == MouseEvent.BUTTON2 ||
          (button == MouseEvent.BUTTON1 && BitUtil.isSet(event.getModifiersEx(), InputEvent.ALT_DOWN_MASK))) {
        action = !Registry.is("debugger.click.disable.breakpoints")
                 ? ClickAction.ENABLE_DISABLE
                 : ClickAction.REMOVE;
      }
      else if (button == MouseEvent.BUTTON1) {
        action = Registry.is("debugger.click.disable.breakpoints")
                 ? ClickAction.ENABLE_DISABLE
                 : ClickAction.REMOVE;
      }
    }
    else {
      assert variant != null;
      if (button == MouseEvent.BUTTON1) {
        action = ClickAction.SET;
      }
    }

    if (action == null) {
      return;
    }
    // FIXME[inline-bp]: what about removal by drag and drop?

    var editor = inlay.getEditor();
    var project = editor.getProject();
    assert project != null; // FIXME[inline-bp]: replace by if?
    var file = editor.getVirtualFile();
    assert file != null; // FIXME[inline-bp]: replace by if?
    var offset = inlay.getOffset();

    switch (action) {
      case SET -> {
        // FIXME[inline-bp]: is it ok to keep variant so long or should we obtain fresh variants and find similar one?
        var breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager();
        var line = editor.getDocument().getLineNumber(offset);
        //noinspection unchecked
        breakpointManager.addLineBreakpoint((XLineBreakpointType)variant.getType(), file.getUrl(), line, variant.createProperties(),
                                            false);
      }
      case ENABLE_DISABLE -> {
        breakpoint.setEnabled(!breakpoint.isEnabled());
      }
      case REMOVE -> {
        if (XDebuggerUtilImpl.removeBreakpointWithConfirmation(breakpoint)) {
          // FIXME[inline-bp]: it's a dirty hack to render inlay as "hovered" just after we clicked on set breakpoint
          //       The problem is that after breakpoint removal we currently recreate all inlays and new ones would not be "hovered".
          //       So we manually propogate this property to future inlay at the same position.
          //       Otherwise there will be flickering:
          //       transparent -> (move mouse) -> hovered -> (click) -> set -> (click) -> transparent -> (move mouse 1px) -> hovered
          //                                                                              ^^^^^^^^^^^ this is bad
          //       One day we would keep old inlays and this hack would gone.
          for (var newInlay : editor.getInlayModel().getInlineElementsInRange(offset, offset, InlineBreakpointInlayRenderer.class)) {
            newInlay.getRenderer().hovered = true;
          }
        }
      }
    }
  }

  @Override
  public void mouseMoved(@NotNull MouseEvent event, @NotNull Point translated) {
    event.consume();
    setHovered(true);
  }

  @Override
  public void mouseExited() {
    setHovered(false);
  }

  private void setHovered(boolean hovered) {
    var wasHovered = this.hovered;
    this.hovered = hovered;
    var cursor = hovered ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : null;
    ((EditorEx)inlay.getEditor()).setCustomCursor(InlineBreakpointInlayRenderer.class, cursor);
    if (wasHovered != hovered) {
      inlay.repaint();
    }
  }
}
