// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.inline;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.Inlay;
import com.intellij.openapi.editor.LineExtensionInfo;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.EditorMouseEvent;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleColoredText;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XNamedValue;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerExecutionPointManager;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.XSourcePositionImpl;
import com.intellij.xdebugger.impl.evaluate.XValueCompactPresentation;
import com.intellij.xdebugger.impl.evaluate.quick.XDebuggerTreeCreator;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.XDebugSessionTab;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueTextRendererImpl;
import com.intellij.xdebugger.ui.DebuggerColors;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.*;

@ApiStatus.Internal
public final class InlineDebugRenderer extends InlineDebugRendererBase {
  public static final String NAME_VALUE_SEPARATION = XDebuggerInlayUtil.INLINE_HINTS_DELIMETER + " ";
  public static final String INDENT = "  ";
  boolean myPopupIsShown = false;
  private final boolean myCustomNode;
  private final XDebugSession mySession;
  private final XValueNodeImpl myValueNode;
  private final XDebuggerTreeCreator myTreeCreator;
  private final XSourcePosition myPosition;
  private SimpleColoredText myPresentation;

  public InlineDebugRenderer(XValueNodeImpl valueNode, @NotNull VirtualFile file, int line, @NotNull XDebugSession session) {
    // We cannot pass any XSourcePosition object here, check EA-841896
    myPosition = XSourcePositionImpl.create(file, line);
    mySession = session;
    myCustomNode = valueNode instanceof InlineWatchNodeImpl;
    myValueNode = valueNode;
    updatePresentation();
    XValueMarkers<?, ?> markers = session instanceof XDebugSessionImpl ?  ((XDebugSessionImpl)session).getValueMarkers() : null;
    myTreeCreator = new XDebuggerTreeCreator(session.getProject(),
                                             session.getDebugProcess().getEditorsProvider(),
                                             session.getCurrentPosition(),
                                             markers);
  }

  public void updatePresentation() {
    TextAttributes attributes = LinePainter.getAttributes(myPosition.getLine(), myPosition.getFile(), mySession);
    SimpleColoredText valuePresentation = LinePainter.createPresentation(myValueNode);
    myPresentation = LinePainter
      .computeVariablePresentationWithChanges(myValueNode.getName(), valuePresentation, attributes, myPosition.getLine(),
                                              mySession.getProject());
  }

  @RequiresBackgroundThread
  @Override
  protected boolean calculateIsInExecutionPoint() {
    return LinePainter.isFullLineHighlighter(mySession, myPosition.getFile(), myPosition.getLine(), false);
  }

  @Override
  public void onClick(Inlay inlay, @NotNull EditorMouseEvent event) {
    int x = event.getMouseEvent().getX();
    boolean isRemoveIconClick = myCustomNode && x >= myRemoveXCoordinate;
    if (isRemoveIconClick) {
      XDebugSessionTab tab = ((XDebugSessionImpl)mySession).getSessionTab();
      if (tab != null) {
        tab.getWatchesView().removeWatches(Collections.singletonList(myValueNode));
      }
      inlay.update();
    }
    else if (x >= myTextStartXCoordinate) {
      handleClick(inlay);
    }
  }

  private void handleClick(Inlay inlay) {
    InlineDebugRenderer inlayRenderer = (InlineDebugRenderer)inlay.getRenderer();
    if (inlayRenderer.myPopupIsShown) {
      return;
    }
    Pair<XValue, String> descriptor = getXValueDescriptor(myValueNode);
    Rectangle bounds = inlay.getBounds();
    Point point = new Point(bounds.x, bounds.y + bounds.height);

    inlayRenderer.myPopupIsShown = true;

    Runnable hidePopupRunnable = () -> {
      ApplicationManager.getApplication().invokeLater(() -> {
        inlayRenderer.myPopupIsShown = false;
      });
    };

    Editor editor = inlay.getEditor();
    InlineValuePopupProvider popupProvider = InlineValuePopupProvider.EP_NAME.findFirstSafe(a -> a.accepts(myValueNode));
    if (popupProvider != null) {
      popupProvider.showPopup(myValueNode, mySession, myPosition, myTreeCreator, editor, point, hidePopupRunnable);
    } else {
      XDebuggerTreeInlayPopup.showTreePopup(myTreeCreator, descriptor, myValueNode, editor, point, myPosition, mySession, hidePopupRunnable);
    }
  }

  @NotNull
  public static Pair<XValue, String> getXValueDescriptor(@NotNull XValueNodeImpl xValueNode) {
    String name = "valueName";
    XValue container = xValueNode.getValueContainer();
    if (container instanceof XNamedValue) {
      name = ((XNamedValue)container).getName();
    }
    return Pair.create(container, name);
  }

  @Override
  @NotNull
  public SimpleColoredText getPresentation() {
    return myPresentation;
  }

  @Override
  public boolean isErrorMessage() {
    return XDebuggerUIConstants.ERROR_MESSAGE_ICON.equals(myValueNode.getIcon());
  }

  @Override
  public boolean isCustomNode() {
    return myCustomNode;
  }

  XValueNodeImpl getValueNode() {
    return myValueNode;
  }

  XSourcePosition getPosition() {
    return myPosition;
  }

  /**
   * @author Konstantin Bulenkov
   */
  public static final class LinePainter {
    private static final Logger LOG = Logger.getInstance(LinePainter.class);
    public static final Key<Map<Variable, VariableValue>> CACHE = Key.create("debug.inline.variables.cache");

    public static SimpleColoredText computeVariablePresentationWithChanges(String name,
                                                                           SimpleColoredText text,
                                                                           TextAttributes attributes,
                                                                           int lineNumber,
                                                                           Project project) {
      Map<Variable, VariableValue> oldValues = getOldValues(project);
      VariableText variableText = computeVariablePresentationWithChanges(name, text, attributes, lineNumber, oldValues);

      SimpleColoredText coloredText = new SimpleColoredText();
      for (LineExtensionInfo info : variableText.infos) {
        TextAttributes textAttributes = new TextAttributes(info.getColor(), info.getBgColor(), info.getEffectColor(),info.getEffectType(), info.getFontType());
        coloredText.append(info.getText(), SimpleTextAttributes.fromTextAttributes(textAttributes));
      }
      return coloredText;
    }

    @NotNull
    private static InlineDebugRenderer.LinePainter.VariableText computeVariablePresentationWithChanges(@NlsSafe String name,
                                                                                                       SimpleColoredText text,
                                                                                                       TextAttributes attributes,
                                                                                                       int lineNumber,
                                                                                                       Map<Variable, VariableValue> oldValues) {
      final VariableText res = new VariableText();
      res.add(new LineExtensionInfo(name, attributes));

      Variable var = new Variable(name, lineNumber);
      VariableValue variableValue = oldValues.get(var);
      if (variableValue == null) {
        variableValue = new VariableValue(text.toString(), null);
        oldValues.put(var, variableValue);
      }
      else if (!StringUtil.equals(text.toString(), variableValue.actual)) {
        variableValue.setOld(variableValue.actual);
        variableValue.actual = text.toString();
      }

      if (!variableValue.isChanged()) {
        ArrayList<String> texts = text.getTexts();
        for (int i = 0; i < texts.size(); i++) {
          String s = texts.get(i);
          TextAttributes attr = Registry.is("debugger.show.values.colorful")
                                ? text.getAttributes().get(i).toTextAttributes()
                                : attributes;
          res.add(new LineExtensionInfo(s, attr));
        }
      }
      else {
        variableValue.produceChangedParts(res.infos);
      }
      return res;
    }

    @NotNull
    public static Map<Variable, VariableValue> getOldValues(@NotNull Project project) {
      Map<Variable, VariableValue> oldValues = project.getUserData(CACHE);
      if (oldValues == null) {
        oldValues = new HashMap<>();
        project.putUserData(CACHE, oldValues);
      }
      return oldValues;
    }

    static @NotNull TextAttributes getAttributes(int lineNumber, @NotNull VirtualFile file, XDebugSession session) {
      return isFullLineHighlighter(session, file, lineNumber, true)
             ? getTopFrameSelectedAttributes() : getNormalAttributes();
    }

    static boolean isFullLineHighlighter(@NotNull XDebugSession session, @NotNull VirtualFile file, int lineNumber,
                                         boolean isToCheckTopFrameOnly) {
      Project project = session.getProject();
      XDebuggerManagerImpl debuggerManager = (XDebuggerManagerImpl)XDebuggerManager.getInstance(project);
      XDebuggerExecutionPointManager executionPointManager = debuggerManager.getExecutionPointManager();
      return executionPointManager.isFullLineHighlighterAt(file, lineNumber, project, isToCheckTopFrameOnly);
    }

    @Nullable
    public static SimpleColoredText createPresentation(@NotNull XValueNodeImpl value) {
      SimpleColoredText text = new SimpleColoredText();
      XValueTextRendererImpl renderer = new XValueTextRendererImpl(text);
      final XValuePresentation presentation = value.getValuePresentation();
      if (presentation == null) return null;
      try {
        if (presentation instanceof XValueCompactPresentation && !value.getTree().isUnderRemoteDebug()) {
          ((XValueCompactPresentation)presentation).renderValue(renderer, value);
        }
        else {
          presentation.renderValue(renderer);
        }
        if (StringUtil.isEmpty(text.toString())) {
          final String type = value.getValuePresentation().getType();
          if (!StringUtil.isEmpty(type)) {
            text.append(type, SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
        }
      }
      catch (Exception e) {
        LOG.error(e);
        return null;
      }
      return text;
    }

    private static TextAttributes getNormalAttributes() {
      TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(DebuggerColors.INLINED_VALUES);
      if (attributes == null || attributes.getForegroundColor() == null) {
       return new TextAttributes(JBColor.lazy(() -> EditorColorsManager.getInstance().isDarkEditor() ? new Color(0x3d8065) : Gray._135), null, null, null, Font.ITALIC);
      }
      return attributes;
    }

    private static TextAttributes getChangedAttributes() {
      TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(DebuggerColors.INLINED_VALUES_MODIFIED);
      if (attributes == null || attributes.getForegroundColor() == null) {
        return new TextAttributes(JBColor.lazy(() -> EditorColorsManager.getInstance().isDarkEditor() ? new Color(0xa1830a) : new Color(0xca8021)), null, null, null, Font.ITALIC);
      }
      return attributes;
    }

    private static TextAttributes getTopFrameSelectedAttributes() {
      TextAttributes attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(DebuggerColors.INLINED_VALUES_EXECUTION_LINE);
      if (attributes == null || attributes.getForegroundColor() == null) {
        //noinspection UseJBColor
        return new TextAttributes(EditorColorsManager.getInstance().isDarkEditor() ? new Color(255, 235, 9) : new Color(0, 255, 86), null, null, null, Font.ITALIC);
      }
      return attributes;
    }

    static class Variable {
      private final int lineNumber;
      private final String name;

      Variable(String name, int lineNumber) {
        this.lineNumber = lineNumber;
        this.name = name;
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Variable variable = (Variable)o;

        if (lineNumber != variable.lineNumber) return false;
        if (!name.equals(variable.name)) return false;

        return true;
      }

      @Override
      public int hashCode() {
        return Objects.hash(lineNumber, name);
      }
    }

    static class VariableValue {
      // TODO: this has to be specified somewhere in XValuePresentation
      private static final java.util.List<Couple<String>> ARRAYS_WRAPPERS = java.util.List.of(Couple.of("[", "]"), Couple.of("{", "}"));
      private static final String ARRAY_DELIMITER = ", ";
      private @NlsSafe String actual;
      private @NlsSafe String old;

      VariableValue(String actual, String old) {
        this.actual = actual;
        this.old = old;
      }

      public boolean isChanged() {
        return old != null && !StringUtil.equals(actual, old) && !isCollecting(actual);
      }

      void produceChangedParts(java.util.List<? super LineExtensionInfo> result) {
        Couple<String> wrapperActual = getArrayWrapper(actual);
        if (wrapperActual != null && getArrayWrapper(old) != null) {
          java.util.List<String> actualParts = getArrayParts(actual);
          java.util.List<String> oldParts = getArrayParts(old);
          //noinspection HardCodedStringLiteral
          result.add(new LineExtensionInfo(wrapperActual.first, getNormalAttributes()));
          for (int i = 0; i < actualParts.size(); i++) {
            if (i < oldParts.size() && StringUtil.equals(actualParts.get(i), oldParts.get(i))) {
              result.add(new LineExtensionInfo(actualParts.get(i), getNormalAttributes()));
            } else {
              result.add(new LineExtensionInfo(actualParts.get(i), getChangedAttributes()));
            }
            if (i != actualParts.size() - 1) {
              result.add(new LineExtensionInfo(ARRAY_DELIMITER, getNormalAttributes()));
            }
          }
          result.add(new LineExtensionInfo(wrapperActual.second, getNormalAttributes()));
          return;
        }

        result.add(new LineExtensionInfo(actual, getChangedAttributes()));
      }

      void setOld(String text) {
        if (!isCollecting(text)) {
          this.old = text;
        }
      }

      //TODO:implement better detection
      static boolean isCollecting(@NotNull String text) {
        return text.contains(XDebuggerUIConstants.getCollectingDataMessage());
      }

      @Nullable
      private static Couple<String> getArrayWrapper(@Nullable String s) {
        if (s == null) {
          return null;
        }
        return ContainerUtil.find(ARRAYS_WRAPPERS, w -> s.startsWith(w.first) && s.endsWith(w.second));
      }

      private static java.util.List<String> getArrayParts(String array) {
        return StringUtil.split(array.substring(1, array.length() - 1), ARRAY_DELIMITER);
      }
    }

    private static class VariableText {
      final List<LineExtensionInfo> infos = new ArrayList<>();

      void add(LineExtensionInfo info) {
        infos.add(info);
      }
    }
  }
}
