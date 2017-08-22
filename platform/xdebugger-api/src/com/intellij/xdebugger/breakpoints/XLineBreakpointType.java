/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import javax.swing.*;
import java.util.Collections;
import java.util.List;

/**
 * Implement this class to support new type of line breakpoints. An implementation should be registered in a plugin.xml:
 * <p>
 * &lt;extensions defaultExtensionNs="com.intellij"&gt;<br>
 * &nbsp;&nbsp;&lt;xdebugger.breakpointType implementation="qualified-class-name"/&gt;<br>
 * &lt;/extensions&gt;
 * <p><p>
 * In order to support actual setting breakpoints in a debugging process create a {@link XBreakpointHandler} implementation and return it  
 * from {@link com.intellij.xdebugger.XDebugProcess#getBreakpointHandlers()} method
 *
 * @author nik
 */
public abstract class XLineBreakpointType<P extends XBreakpointProperties> extends XBreakpointType<XLineBreakpoint<P>,P> {
  protected XLineBreakpointType(@NonNls @NotNull final String id, @Nls @NotNull final String title) {
    super(id, title);
  }

  /**
   * Return <code>true<code> if breakpoint can be put on {@code line} in {@code file}
   */
  public boolean canPutAt(@NotNull VirtualFile file, int line, @NotNull Project project) {
    return false;
  }

  /**
   * return non-null value if a breakpoint should have specific properties besides containing file and line. These properties will be stored in
   * {@link XBreakpoint} instance and can be obtained by using {@link XBreakpoint#getProperties()} method
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
   * Source position for line breakpoint by default is determined by its file and line
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
   * Default line breakpoints aren't supported
   */
  @Override
  public final XLineBreakpoint<P> createDefaultBreakpoint(@NotNull XBreakpointCreator<P> creator) {
    return null;
  }

  public List<? extends AnAction> getAdditionalPopupMenuActions(@NotNull XLineBreakpoint<P> breakpoint, @Nullable XDebugSession currentSession) {
    return Collections.emptyList();
  }

  public Icon getTemporaryIcon() {
    return AllIcons.Debugger.Db_temporary_breakpoint;
  }

  /**
   * Priority is considered when several breakpoint types can be set on the same code line,
   * in this case we choose type with the highest priority
   */
  public int getPriority() {
    return 0;
  }

  /**
   * Return true if this breakpoint could be hit on lines other than the one specified,
   * an example is method breakpoint in java - it could be hit on any method overriding the one specified
   */
  public boolean canBeHitInOtherPlaces() {
    return false;
  }

  /**
   * @return range to highlight on the line, null to highlight the whole line
   */
  @Nullable
  public TextRange getHighlightRange(XLineBreakpoint<P> breakpoint) {
    return null;
  }

  /**
   * Return a list of variants if there can be more than one breakpoint on the line
   */
  @NotNull
  public List<? extends XLineBreakpointVariant> computeVariants(@NotNull Project project, @NotNull XSourcePosition position) {
    return Collections.emptyList();
  }

  @NotNull
  public Promise<List<? extends XLineBreakpointVariant>> computeVariantsAsync(@NotNull Project project, @NotNull XSourcePosition position) {
    return Promise.resolve(computeVariants(project, position));
  }

  public abstract class XLineBreakpointVariant {
    public abstract String getText();

    @Nullable
    public abstract Icon getIcon();

    @Nullable
    public abstract TextRange getHighlightRange();

    @Nullable
    public abstract P createProperties();
  }

  public class XLineBreakpointAllVariant extends XLineBreakpointVariant {
    protected final XSourcePosition mySourcePosition;

    public XLineBreakpointAllVariant(@NotNull XSourcePosition position) {
      mySourcePosition = position;
    }

    @Override
    public String getText() {
      return "All";
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
