// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.xdebugger.breakpoints;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
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
    return fileLineDisplayText(breakpoint.getPresentableFilePath(), breakpoint.getLine());
  }

  private static String fileLineDisplayText(String path, int line) {
    return XDebuggerBundle.message("xbreakpoint.default.display.text", line + 1, path);
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
    return fileLineDisplayText(breakpoint.getShortFilePath(), breakpoint.getLine());
  }

  /**
   * Default line breakpoints aren't supported.
   */
  @Override
  public final XLineBreakpoint<P> createDefaultBreakpoint(@NotNull XBreakpointCreator<P> creator) {
    return null;
  }

  // Preserved for API compatibility
  @SuppressWarnings("RedundantMethodOverride")
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

  public abstract class XLineBreakpointVariant {
    @NotNull
    @Nls
    public abstract String getText();

    @Nullable
    public abstract Icon getIcon();

    @Nullable
    public abstract TextRange getHighlightRange();

    @Nullable
    public abstract P createProperties();

    public final XLineBreakpointType<P> getType() {
      return XLineBreakpointType.this;
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
  }
}
