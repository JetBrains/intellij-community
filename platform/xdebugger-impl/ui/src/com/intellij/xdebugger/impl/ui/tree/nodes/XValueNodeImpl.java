// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.nodes;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.frame.XInlineDebuggerDataCallback;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValuePlace;
import com.intellij.xdebugger.frame.presentation.XValuePresentation;
import com.intellij.xdebugger.impl.XSourceKind;
import com.intellij.xdebugger.impl.frame.XDebugView;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.inline.XDebuggerInlayUtil;
import com.intellij.xdebugger.impl.pinned.items.PinToTopUtilKt;
import com.intellij.xdebugger.impl.pinned.items.actions.XDebuggerPinToTopAction;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XRendererDecoratorPresentation;
import com.intellij.xdebugger.settings.XDebuggerSettingsManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import javax.swing.Icon;
import java.awt.event.MouseEvent;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicReference;

public class XValueNodeImpl extends XValueContainerNode<XValue> implements XValueNodeEx, XCompositeNode, XValueNodePresentationConfigurator.ConfigurableXValueNode, RestorableStateNode {
  private static final Logger LOG = Logger.getInstance(XValueNodeImpl.class);

  public static final Comparator<XValueNodeImpl> COMPARATOR = (o1, o2) -> StringUtil.naturalCompare(o1.getName(), o2.getName());

  private static final int MAX_NAME_LENGTH = 100;

  private final @NlsSafe String myName;
  private @Nullable String myRawValue;
  private XFullValueEvaluator myFullValueEvaluator;
  // Should only update on EDT, keeping atomic just in case (all informal contracts may change at any time)
  private final @NotNull AtomicReference<@Nullable XDebuggerTreeNodeHyperlink> myAdditionalHyperLink = new AtomicReference<>();
  private boolean myChanged;
  private XValuePresentation myValuePresentation;
  private @Nullable Icon myInlayIcon;

  //todo annotate 'name' with @NotNull
  public XValueNodeImpl(XDebuggerTree tree, @Nullable XDebuggerTreeNode parent, String name, @NotNull XValue value) {
    super(tree, parent, true, value);
    myName = name;

    // todo: should be rewritten, this code passes partially initialized 'this' into a call
    value.computePresentation(this, XValuePlace.TREE);

    // add "Collecting" message only if computation is not yet done
    if (!isComputed()) {
      if (myName != null) {
        myText.append(myName, XDebuggerUIConstants.VALUE_NAME_ATTRIBUTES);
        myText.append(XDebuggerUIConstants.EQ_TEXT, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      myText.append(XDebuggerUIConstants.getCollectingDataMessage(), XDebuggerUIConstants.COLLECTING_DATA_HIGHLIGHT_ATTRIBUTES);
    }
  }

  XValueNodeImpl(XValueNodeImpl node, @NotNull XValue value) {
    super(node.getTree(), (XDebuggerTreeNode)node.getParent(), true, value);
    myName = node.getName();
  }

  @Override
  public void setPresentation(@Nullable Icon icon, @NonNls @Nullable String type, @NonNls @NotNull String value, boolean hasChildren) {
    XValueNodePresentationConfigurator.setPresentation(icon, type, value, hasChildren, this);
  }

  @Override
  public void setPresentation(@Nullable Icon icon, @NotNull XValuePresentation presentation, boolean hasChildren) {
    XValueNodePresentationConfigurator.setPresentation(icon, presentation, hasChildren, this);
  }

  @Override
  public void applyPresentation(@Nullable Icon icon, @NotNull XValuePresentation valuePresentation, boolean hasChildren) {
    // extra check for obsolete nodes - tree root was changed
    // too dangerous to put this into isObsolete - it is called from anywhere, not only EDT
    if (isObsolete()) return;

    setIcon(icon);
    boolean alreadyHasInline = myValuePresentation != null;
    myValuePresentation = valuePresentation;
    myRawValue = XValuePresentationUtil.computeValueText(valuePresentation);
    if (shouldUpdateInlineDebuggerData() && !alreadyHasInline) {
      updateInlineDebuggerData();
    }
    updateText();
    setLeaf(!hasChildren);
    fireNodeChanged();
    myTree.nodeLoaded(this, myName);
  }

  protected boolean shouldUpdateInlineDebuggerData() {
    return XDebuggerSettingsManager.getInstance().getDataViewSettings().isShowValuesInline();
  }

  @ApiStatus.Internal
  protected boolean isChanged() {
    return myChanged;
  }

  public void setInlayIcon(@Nullable Icon icon) {
    myInlayIcon = icon;
  }

  @Nullable
  public Icon getInlayIcon() {
    return myInlayIcon;
  }

  private void updateInlineDebuggerData() {
    try {
      XDebugSessionProxy session = XDebugView.getSessionProxy(getTree());
      if (session == null) return;
      XStackFrame currentFrame = session.getCurrentStackFrame();
      if (currentFrame == null) return;
      final XSourcePosition mainPosition = session.getFrameSourcePosition(currentFrame, XSourceKind.MAIN);
      final XSourcePosition altPosition = session.getFrameSourcePosition(currentFrame, XSourceKind.ALTERNATIVE);
      if (mainPosition == null && altPosition == null) {
        return;
      }

      final XInlineDebuggerDataCallback callback = new XInlineDebuggerDataCallback() {
        @Override
        public void computed(XSourcePosition position) {
          if (isObsolete() || position == null) return;
          VirtualFile file = position.getFile();
          // filter out values from other files
          VirtualFile mainFile = mainPosition != null ? mainPosition.getFile() : null;
          VirtualFile altFile = altPosition != null ? altPosition.getFile() : null;
          if (!Comparing.equal(mainFile, file) && !Comparing.equal(altFile, file)) {
            return;
          }
          final Document document = FileDocumentManager.getInstance().getDocument(file);
          if (document == null) return;

          int line = position.getLine();
          if (line >= 0) {
            XDebuggerInlayUtil.getInstance(session.getProject()).createLineEndInlay(XValueNodeImpl.this, session, file, line);
          }
        }
      };

      XValue xValue = getValueContainer();
      if (xValue.computeInlineDebuggerData(callback) == ThreeState.UNSURE) {
        xValue.computeSourcePosition(callback::computed);
      }
    }
    catch (Exception ignore) {
    }
  }

  @Override
  public void setFullValueEvaluator(final @NotNull XFullValueEvaluator fullValueEvaluator) {
    invokeNodeUpdate(() -> {
      myFullValueEvaluator = fullValueEvaluator;
      fireNodeChanged();
    });
  }

  @Override
  public void addAdditionalHyperlink(@NotNull XDebuggerTreeNodeHyperlink link) {
    invokeNodeUpdate(() -> {
      if (!myAdditionalHyperLink.compareAndSet(null, link)) {
        LOG.warn("Additional hyperlink already set; having more than one is not supported");
      }
      fireNodeChanged();
    });
  }

  @Override
  public void clearAdditionalHyperlinks() {
    invokeNodeUpdate(() -> {
      myAdditionalHyperLink.set(null);
    });
  }

  public boolean hasLinks() {
    return myFullValueEvaluator != null && myFullValueEvaluator.isEnabled() || myAdditionalHyperLink.get() != null;
  }

  @Override
  public void clearFullValueEvaluator() {
    myFullValueEvaluator = null;
  }

  private void updateText() {
    myText.clear();
    XValueMarkers<?, ?> markers = myTree.getValueMarkers();
    if (markers != null) {
      ValueMarkup markup = markers.getMarkup(getValueContainer());
      if (markup != null) {
        XCustomizableTextRenderer renderer = createTextRenderer(myText, myValuePresentation);
        SimpleTextAttributes attributes = new SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, markup.getColor());
        renderer.renderRaw("[" + markup.getText() + "] ", attributes);
      }
    }
    if (myValuePresentation.isShowName()) {
      appendName();
    }
    buildText(myValuePresentation, myText);
  }

  private void appendName() {
    if (!StringUtil.isEmpty(myName)) {
      XCustomizableTextRenderer renderer = createTextRenderer(myText, myValuePresentation);
      SimpleTextAttributes attributes = isChanged() ? XDebuggerUIConstants.CHANGED_VALUE_ATTRIBUTES : XDebuggerUIConstants.VALUE_NAME_ATTRIBUTES;
      XValuePresentationUtil.renderName(myName, MAX_NAME_LENGTH, s -> renderer.renderRaw(s, attributes));
    }
  }

  @ApiStatus.Internal
  @Override
  public @NotNull XValue getXValue() {
    return getValueContainer();
  }

  private static @NotNull XCustomizableTextRenderer createTextRenderer(ColoredTextContainer text,
                                                                       XValuePresentation valuePresentation) {
    XCustomizableTextRenderer renderer = new XValueTextRendererImpl(text);
    if (valuePresentation instanceof XRendererDecoratorPresentation decoratorPresentation) {
      renderer = decoratorPresentation.decorate(renderer);
    }
    return renderer;
  }

  public static void buildText(@NotNull XValuePresentation valuePresenter, @NotNull ColoredTextContainer text) {
    buildText(valuePresenter, text, true);
  }

  public static void buildText(@NotNull XValuePresentation valuePresenter, @NotNull ColoredTextContainer text, boolean appendSeparator) {
    XCustomizableTextRenderer renderer = createTextRenderer(text, valuePresenter);
    if (appendSeparator) {
      String separator = valuePresenter.getSeparator();
      if (StringUtil.isNotEmpty(separator)) {
        renderer.renderRaw(separator, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    }
    String type = valuePresenter.getType();
    if (type != null) {
      renderer.renderRaw("{" + type + "} ", XDebuggerUIConstants.TYPE_ATTRIBUTES);
    }
    valuePresenter.renderValue(renderer);
  }

  @Override
  public void markChanged() {
    if (myChanged) return;

    ThreadingAssertions.assertEventDispatchThread();
    myChanged = true;
    if (myName != null && myValuePresentation != null) {
      updateText();
      fireNodeChanged();
    }
  }

  /** always compute evaluate expression from the base value container to avoid recalculation for watches
   * @see WatchNodeImpl#getValueContainer()
   */
  public final @NotNull Promise<XExpression> calculateEvaluationExpression() {
    return myValueContainer.calculateEvaluationExpression();
  }

  public @Nullable XFullValueEvaluator getFullValueEvaluator() {
    return myFullValueEvaluator;
  }

  @Override
  public @Nullable XDebuggerTreeNodeHyperlink getLink() {
    if (myFullValueEvaluator != null && myFullValueEvaluator.isEnabled()) {
      return new XDebuggerTreeNodeHyperlink(myFullValueEvaluator.getLinkText(), myFullValueEvaluator.getLinkAttributes()) {
        @Override
        public boolean alwaysOnScreen() {
          return true;
        }

        @Override
        public void onClick(MouseEvent event) {
          if (myFullValueEvaluator.isShowValuePopup()) {
            DebuggerUIUtil.showValuePopup(myFullValueEvaluator, event, myTree.getProject(), null);
          }
          else {
            new HeadlessValueEvaluationCallback(XValueNodeImpl.this).startFetchingValue(myFullValueEvaluator);
          }
          event.consume();
        }
      };
    }
    return null;
  }

  @Override
  public void appendToComponent(@NotNull ColoredTextContainer component) {
    super.appendToComponent(component);

    XDebuggerTreeNodeHyperlink hyperlink = myAdditionalHyperLink.get();
    if (hyperlink != null) {
      component.append(hyperlink.getLinkText(), hyperlink.getTextAttributes(), hyperlink);
    }
  }

  @Override
  public @Nullable String getName() {
    return myName;
  }

  public @Nullable XValuePresentation getValuePresentation() {
    return myValuePresentation;
  }

  @Override
  public @Nullable String getRawValue() {
    return myRawValue;
  }

  @Override
  public boolean isComputed() {
    return myValuePresentation != null;
  }

  public void setValueModificationStarted() {
    ThreadingAssertions.assertEventDispatchThread();
    myRawValue = null;
    myText.clear();
    appendName();
    XValuePresentationUtil.appendSeparator(myText, myValuePresentation.getSeparator());
    myText.append(XDebuggerUIConstants.getModifyingValueMessage(), XDebuggerUIConstants.MODIFYING_VALUE_HIGHLIGHT_ATTRIBUTES);
    setLeaf(true);
    fireNodeStructureChanged();
  }

  @Override
  public String toString() {
    return getName();
  }

  @Override
  public @Nullable Object getIconTag() {
    if (!getTree().getPinToTopManager().isEnabled()) {
        return null;
    }

    if (!PinToTopUtilKt.canBePinned(this))
      return null;

    return new XDebuggerTreeNodeHyperlink(XDebuggerBundle.message("xdebugger.pin.to.top.action")) {
      @Override
      public void onClick(MouseEvent event) {
        XDebuggerPinToTopAction.Companion.pinToTopField(event, XValueNodeImpl.this);
      }
    };
  }
}