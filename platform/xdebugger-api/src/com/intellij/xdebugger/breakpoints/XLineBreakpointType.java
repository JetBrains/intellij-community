// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.xdebugger.breakpoints;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * Implement this class to support new types of line breakpoints.
 * An implementation should be registered in a plugin.xml:
 * <p>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;xdebugger.breakpointType implementation="qualified-class-name"/&gt;<br>
 * &lt;/extensions&gt;
 * <p>
 * In order to support actually setting breakpoints in a debugging process,
 * create a {@link XBreakpointHandler} implementation
 * and return it from {@link com.intellij.xdebugger.XDebugProcess#getBreakpointHandlers()}.
 */
public abstract class XLineBreakpointType<P extends XBreakpointProperties> extends XBreakpointType<XLineBreakpoint<P>,P> {
  protected XLineBreakpointType(@NonNls @NotNull final String id, @Nls @NotNull final String title) {
    super(id, title);
  }

  /**
   * Return {@code true} if breakpoint can be put on {@code line} in {@code file}.
   */
  public boolean canPutAt(@NotNull VirtualFile file, int line, @NotNull Project project) {
    return false;
  }

  /**
   * Return a non-null value if a breakpoint should have specific properties besides the file and line.
   * These properties are stored in an {@link XBreakpoint} instance
   * and can be obtained by {@link XBreakpoint#getProperties()}.
   */
  @Nullable
  public abstract P createBreakpointProperties(@NotNull VirtualFile file, int line);

  @Override
  public String getDisplayText(final XLineBreakpoint<P> breakpoint) {
    return filePositionDisplayText(breakpoint.getPresentableFilePath(), breakpoint);
  }

  @Nls
  private String filePositionDisplayText(String path, XLineBreakpoint<P> breakpoint) {
    var line = breakpoint.getLine();
    var column = getColumn(breakpoint);
    if (column <= 0) {
      return XDebuggerBundle.message("xbreakpoint.default.display.text", line + 1, path);
    } else {
      return XDebuggerBundle.message("xbreakpoint.default.display.text.with.column", line + 1, column + 1, path);
    }
  }

  /**
   * Column index (zero-based) of this line breakpoint:
   * <ul>
   *   <li><em>positive</em> for inline breakpoints,</li>
   *   <li><em>zero</em> for regular line breakpoint,</li>
   *   <li><em>negative</em> if column number is not available.</li>
   * </ul>
   */
  public int getColumn(XLineBreakpoint<P> breakpoint) {

    return ReadAction.compute(() -> {
      var range = breakpoint.getType().getHighlightRange(breakpoint);
      if (range == null) return 0; // full line breakpoint
      var offset = range.getStartOffset();

      var file = VirtualFileManager.getInstance().findFileByUrl(breakpoint.getFileUrl());
      if (file == null) return -1;
      var document = FileDocumentManager.getInstance().getDocument(file);
      if (document == null) return -1;
      if (!XDebuggerUtil.areInlineBreakpointsEnabled(file)) return -1;
      if (0 > offset || offset > document.getTextLength()) return -1;
      return offset - document.getLineStartOffset(document.getLineNumber(offset));
    });
  }

  /**
   * Laconic breakpoint variant description with specification of its kind (type of target).
   * Primarily used for tooltip in the editor, when exact target is obvious but overall semantics might be unclear.
   * E.g.: "Line breakpoint", "Lambda breakpoint", "Field breakpoint".
   *
   * @see XBreakpointType#getGeneralDescription(XBreakpoint)
   */
  @NotNull
  @Nls
  protected String getGeneralDescription(XLineBreakpointVariant variant) {
    // Default implementation just for API backward compatibility, it's highly recommended to properly implement this method.
    return variant.getText();
  }

  /**
   * The source position for a line breakpoint defaults to its file and line.
   */
  @Override
  public XSourcePosition getSourcePosition(@NotNull XBreakpoint<P> breakpoint) {
    return null;
  }

  @Override
  public String getShortText(XLineBreakpoint<P> breakpoint) {
    return filePositionDisplayText(breakpoint.getShortFilePath(), breakpoint);
  }

  /**
   * Default line breakpoints aren't supported.
   */
  @Override
  public final XLineBreakpoint<P> createDefaultBreakpoint(@NotNull XBreakpointCreator<P> creator) {
    return null;
  }

  // Preserved for API compatibility
  @Override
  public List<? extends AnAction> getAdditionalPopupMenuActions(@NotNull XLineBreakpoint<P> breakpoint, @Nullable XDebugSession currentSession) {
    return super.getAdditionalPopupMenuActions(breakpoint, currentSession);
  }

  public Icon getTemporaryIcon() {
    return AllIcons.Debugger.Db_set_breakpoint;
  }

  /**
   * The priority is considered when several breakpoint types can be set inside a folded code block,
   * in this case we choose the type with the highest priority.
   * The priority also affects types sorting in various places.
   */
  public int getPriority() {
    return 0;
  }

  /**
   * Return true if this breakpoint could be hit on lines other than the one specified.
   * For example, a Java method breakpoint can be hit on any method overriding the one specified.
   */
  public boolean canBeHitInOtherPlaces() {
    return false;
  }

  /**
   * @return range to highlight on the line, {@code null} to highlight the whole line
   */
  @Nullable
  public TextRange getHighlightRange(XLineBreakpoint<P> breakpoint) {
    return null;
  }

  /**
   * Return the list of variants if there can be more than one breakpoint on the line.
   */
  @NotNull
  public List<? extends XLineBreakpointVariant> computeVariants(@NotNull Project project, @NotNull XSourcePosition position) {
    return Collections.emptyList();
  }

  @NotNull
  public Promise<List<? extends XLineBreakpointVariant>> computeVariantsAsync(@NotNull Project project, @NotNull XSourcePosition position) {
    return Promises.resolvedPromise(computeVariants(project, position));
  }

  /**
   * Return whether given {@code breakpoint} corresponds to given {@code variant}.
   * I.e., this breakpoint was created using this variant.
   */
  public boolean variantAndBreakpointMatch(@NotNull XLineBreakpoint<P> breakpoint, @NotNull XLineBreakpointVariant variant) {
    // By default, we only compare highlight ranges, however, feel free to override and implement more sophisticated logic
    // (i.e., it may be required if there are different breakpoint variants starting at the same location,
    // e.g., to resolve issues like IDEA-337165).

    var r1 = getHighlightRange(breakpoint);
    var r2 = variant.getHighlightRange();

    if (r1 == null && r2 == null) {
      // null means "whole line"
      return true;
    }

    if (r1 != null && r2 != null) {
      return r1.getStartOffset() == r2.getStartOffset();
    }

    return false;
  }

  public boolean changeLine(@NotNull XLineBreakpoint<P> breakpoint, int newLine, @NotNull Project project) {
    return true;
  }

  public abstract class XLineBreakpointVariant {
    @NotNull
    @Nls
    public abstract String getText();

    @Nullable
    public abstract Icon getIcon();

    @Nullable
    public abstract TextRange getHighlightRange();

    /**
     * @return true iff this variant corresponds to breakpoint hitting at all line locations
     *         (i.e., "all", "line and all lambdas")
     */
    public boolean isMultiVariant() {
      return false;
    }

    @Nullable
    public abstract P createProperties();

    public final XLineBreakpointType<P> getType() {
      return XLineBreakpointType.this;
    }

    @NotNull
    @Nls
    public final String getTooltipDescription() {
      return getType().getGeneralDescription(this);
    }

    @Override
    public String toString() {
      return getType() + ": " + getText();
    }
  }

  public class XLineBreakpointAllVariant extends XLineBreakpointVariant {
    protected final XSourcePosition mySourcePosition;

    public XLineBreakpointAllVariant(@NotNull XSourcePosition position) {
      mySourcePosition = position;
    }

    @NotNull
    @Override
    public String getText() {
      return XDebuggerBundle.message("breakpoint.variant.text.all");
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return AllIcons.Debugger.MultipleBreakpoints;
    }

    @Nullable
    @Override
    public TextRange getHighlightRange() {
      return null;
    }

    @Override
    public boolean isMultiVariant() {
      // Historically, base class for all variants was "all" variant.
      return true;
    }

    @Override
    @Nullable
    public P createProperties() {
      return createBreakpointProperties(mySourcePosition.getFile(),
                                        mySourcePosition.getLine());
    }
  }

  public class XLinePsiElementBreakpointVariant extends XLineBreakpointAllVariant {
    private final PsiElement myElement;

    public XLinePsiElementBreakpointVariant(@NotNull XSourcePosition position, PsiElement element) {
      super(position);

      myElement = element;
    }

    @Override
    public Icon getIcon() {
      return myElement.getIcon(0);
    }

    @NotNull
    @Override
    public String getText() {
      return StringUtil.shortenTextWithEllipsis(myElement.getText(), 100, 0);
    }

    @Override
    public TextRange getHighlightRange() {
      return myElement.getTextRange();
    }

    @Override
    public boolean isMultiVariant() {
      return false;
    }
  }
}
