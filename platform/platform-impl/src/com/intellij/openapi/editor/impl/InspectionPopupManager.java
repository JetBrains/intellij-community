// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ide.HelpTooltip;
import com.intellij.ide.PowerSaveMode;
import com.intellij.ide.actions.ActionsCollector;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorBundle;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AncestorListenerAdapter;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.DropDownLink;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.popup.PopupState;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;

final class InspectionPopupManager {
  private final ExtensionPointName<InspectionPopupLevelChangePolicy> EP_NAME = ExtensionPointName.create("com.intellij.inspectionPopupLevelChangePolicy");
  private static final int DELTA_X = 6;
  private static final int DELTA_Y = 6;

  private final Supplier<? extends @NotNull AnalyzerStatus> statusSupplier;
  private final Editor myEditor;
  private final AnAction compactViewAction;

  private final JPanel myContent = new JPanel(new GridBagLayout());
  private final ComponentPopupBuilder myPopupBuilder;
  private final Map<String, JProgressBar> myProgressBarMap = new HashMap<>();
  private final AncestorListener myAncestorListener;
  private final JBPopupListener myPopupListener;
  private final PopupState<JBPopup> myPopupState = PopupState.forPopup();
  private final Alarm popupAlarm = new Alarm();
  private final List<DropDownLink<?>> levelLinks = new ArrayList<>();

  private JBPopup myPopup;
  private boolean insidePopup;

  InspectionPopupManager(@NotNull Supplier<? extends @NotNull AnalyzerStatus> statusSupplier, @NotNull Editor editor, @NotNull AnAction compactViewAction) {
    this.statusSupplier = statusSupplier;
    this.myEditor = editor;
    this.compactViewAction = compactViewAction;

    myContent.setOpaque(true);
    myContent.setBackground(UIUtil.getToolTipBackground());

    myPopupBuilder = JBPopupFactory.getInstance().createComponentPopupBuilder(myContent, null).
      setCancelOnClickOutside(true).
      setCancelCallback(() -> getAnalyzerStatus().getController().canClosePopup());

    myAncestorListener = new AncestorListenerAdapter() {
      @Override
      public void ancestorMoved(AncestorEvent event) {
        hidePopup();
      }
    };

    myPopupListener = new JBPopupListener() {
      @Override
      public void onClosed(@NotNull LightweightWindowEvent event) {
        statusSupplier.get().getController().onClosePopup();
        myEditor.getComponent().removeAncestorListener(myAncestorListener);
      }
    };

    myContent.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent event) {
        insidePopup = true;
      }

      @Override
      public void mouseExited(MouseEvent event) {
        Point point = event.getPoint();
        if (!myContent.getBounds().contains(point) || point.x == 0 || point.y == 0) {
          insidePopup = false;
          if (canClose()) {
            hidePopup();
          }
        }
      }
    });
  }

  private boolean canClose() {
    return !insidePopup && levelLinks.stream().allMatch(l -> l.getPopupState().isHidden());
  }

  void updateUI() {
    IJSwingUtilities.updateComponentTreeUI(myContent);
  }

  void scheduleShow(@NotNull InputEvent event) {
    popupAlarm.cancelAllRequests();
    popupAlarm.addRequest(() -> showPopup(event), Registry.intValue("ide.tooltip.initialReshowDelay"));
  }

  void scheduleHide() {
    popupAlarm.cancelAllRequests();
    popupAlarm.addRequest(() -> {
      if (canClose()) {
        hidePopup();
      }
    }, Registry.intValue("ide.tooltip.initialDelay.highlighter"));
  }

  private void showPopup(@NotNull InputEvent event) {
    hidePopup();
    if (myPopupState.isRecentlyHidden() || AnalyzerStatus.isEmpty(getAnalyzerStatus())) return; // do not show new popup

    updateContentPanel(getAnalyzerStatus().getController());

    myPopup = myPopupBuilder.createPopup();
    myPopup.addListener(myPopupListener);
    myPopupState.prepareToShow(myPopup);
    myEditor.getComponent().addAncestorListener(myAncestorListener);

    JComponent owner = (JComponent)event.getComponent();
    Dimension size = myContent.getPreferredSize();
    size.width = Math.max(size.width, JBUIScale.scale(296));

    RelativePoint point = new RelativePoint(owner,
                                            new Point(owner.getWidth() - owner.getInsets().right + JBUIScale.scale(DELTA_X) - size.width,
                                                      owner.getHeight() + JBUIScale.scale(DELTA_Y)));

    myPopup.setSize(size);
    myPopup.show(point);
  }

  void hidePopup() {
    if (myPopup != null && !myPopup.isDisposed()) {
      myPopup.cancel();
    }
    myPopup = null;
  }

  @NotNull
  private AnalyzerStatus getAnalyzerStatus() {
    return statusSupplier.get();
  }

  private void updateContentPanel(@NotNull UIController controller) {
    java.util.List<PassWrapper> passes = getAnalyzerStatus().getPasses();
    Set<String> presentableNames = ContainerUtil.map2Set(passes, p -> p.getPresentableName());

    if (!presentableNames.isEmpty() && myProgressBarMap.keySet().equals(presentableNames)) {
      for (PassWrapper pass : passes) {
        myProgressBarMap.get(pass.getPresentableName()).setValue(pass.toPercent());
      }
      return;
    }
    myContent.removeAll();
    levelLinks.clear();

    GridBag gc = new GridBag().nextLine().next().
      anchor(GridBagConstraints.LINE_START).
      weightx(1).
      fillCellHorizontally().
      insets(10, 10, 10, 0);

    boolean hasTitle = StringUtil.isNotEmpty(getAnalyzerStatus().getTitle());

    if (hasTitle) {
      myContent.add(new JLabel(XmlStringUtil.wrapInHtml(getAnalyzerStatus().getTitle())), gc);
    }
    else if (StringUtil.isNotEmpty(getAnalyzerStatus().getDetails())) {
      myContent.add(new JLabel(XmlStringUtil.wrapInHtml(getAnalyzerStatus().getDetails())), gc);
    }
    else if (!getAnalyzerStatus().getExpandedStatus().isEmpty() && getAnalyzerStatus().getAnalyzingType() != AnalyzingType.EMPTY) {
      myContent.add(createDetailsPanel(), gc);
    }

    Presentation presentation = new Presentation();
    presentation.setIcon(AllIcons.Actions.More);
    presentation.putClientProperty(ActionButton.HIDE_DROPDOWN_ICON, Boolean.TRUE);

    java.util.List<AnAction> actions = controller.getActions();
    if (!actions.isEmpty()) {
      ActionButton menuButton = new ActionButton(new MenuAction(actions, compactViewAction),
                                                 presentation,
                                                 ActionPlaces.EDITOR_POPUP,
                                                 ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
        @Override
        protected DataContext getDataContext() {
          return ((EditorEx)myEditor).getDataContext();
        }
      };

      myContent.add(menuButton, gc.next().anchor(GridBagConstraints.LINE_END).weightx(0).insets(10, 6, 10, 6));
    }

    myProgressBarMap.clear();
    JPanel myProgressPanel = new NonOpaquePanel(new GridBagLayout());
    GridBag progressGC = new GridBag();
    for (PassWrapper pass : passes) {
      myProgressPanel.add(new JLabel(pass.getPresentableName() + ": "),
                          progressGC.nextLine().next().anchor(GridBagConstraints.LINE_START).weightx(0).insets(0, 10, 0, 6));

      JProgressBar pb = new JProgressBar(0, 100);
      pb.setValue(pass.toPercent());
      myProgressPanel.add(pb, progressGC.next().anchor(GridBagConstraints.LINE_START).weightx(1).fillCellHorizontally().insets(0, 0, 0, 6));
      myProgressBarMap.put(pass.getPresentableName(), pb);
    }

    myContent.add(myProgressPanel, gc.nextLine().next().anchor(GridBagConstraints.LINE_START).fillCellHorizontally().coverLine().weightx(1));

    if (hasTitle) {
      int topIndent = !myProgressBarMap.isEmpty() ? 10 : 0;
      gc.nextLine().next().anchor(GridBagConstraints.LINE_START).fillCellHorizontally().coverLine().weightx(1).insets(topIndent, 10, 10, 6);

      if (StringUtil.isNotEmpty(getAnalyzerStatus().getDetails())) {
        myContent.add(new JLabel(XmlStringUtil.wrapInHtml(getAnalyzerStatus().getDetails())), gc);
      }
      else if (!getAnalyzerStatus().getExpandedStatus().isEmpty() && getAnalyzerStatus().getAnalyzingType() != AnalyzingType.EMPTY) {
        myContent.add(createDetailsPanel(), gc);
      }
      else if (!passes.isEmpty()){
        myProgressPanel.setBorder(JBUI.Borders.emptyBottom(12));
      }
    }

    myContent.add(createLowerPanel(controller),
                  gc.nextLine().next().anchor(GridBagConstraints.LINE_START).fillCellHorizontally().coverLine().weightx(1));
  }

  void updateVisiblePopup() {
    if (myPopup != null && myPopup.isVisible()) {
      updateContentPanel(getAnalyzerStatus().getController());

      Dimension size = myContent.getPreferredSize();
      size.width = Math.max(size.width, JBUIScale.scale(296));
      myPopup.setSize(size);
    }
  }

  private @NotNull JComponent createDetailsPanel() {
    @Nls StringBuilder text = new StringBuilder();
    for (int i = 0; i < getAnalyzerStatus().getExpandedStatus().size(); i++) {
      boolean last = i == getAnalyzerStatus().getExpandedStatus().size() - 1;
      StatusItem item = getAnalyzerStatus().getExpandedStatus().get(i);

      String detailsText = item.getDetailsText();
      text.append(detailsText != null ? detailsText : item.getText());
      if (!last) {
        text.append(", ");
      }
      else if (getAnalyzerStatus().getAnalyzingType() != AnalyzingType.COMPLETE) {
        text.append(" ").append(EditorBundle.message("iw.found.so.far.suffix"));
      }
    }

    return new JLabel(text.toString());
  }

  private @NotNull JPanel createLowerPanel(@NotNull UIController controller) {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBag gc = new GridBag().nextLine();

    if (PowerSaveMode.isEnabled()) {
      panel.add(new TrackableLinkLabel(EditorBundle.message("iw.disable.powersave"), () ->{
                  PowerSaveMode.setEnabled(false);
                  hidePopup();
                }),
                gc.next().anchor(GridBagConstraints.LINE_START));
    }
    else {
      java.util.List<LanguageHighlightLevel> levels = controller.getHighlightLevels();

      String msg = null;

      for (InspectionPopupLevelChangePolicy extension: EP_NAME.getExtensionList()) {
        msg = extension.getUnavailabilityReason(myEditor);
        if (msg != null) {
          break;
        }
      }

      if (levels.size() == 1) {
        String prefix = EditorBundle.message("iw.highlight.label") + " ";
        GridBag constrains = gc.next();
        // do not create lower panel for code with me guests with no write access
        if (msg == null) {
          DropDownLink<?> link = createDropDownLink(levels.get(0), controller, prefix);
          levelLinks.add(link);
          panel.add(link, constrains);
        }
        else {
          JLabel noAccessLabel = createNoChangeLabel(levels.get(0), prefix, msg);
          panel.add(noAccessLabel, constrains);
        }
      }
      else if (levels.size() > 1) {
        for(LanguageHighlightLevel level: levels) {
          String prefix = level.getLangID() + ": ";
          GridBag constrains = gc.next().anchor(GridBagConstraints.LINE_START).gridx > 0 ? gc.insetLeft(8) : gc;
          // do not create lower panel for code with me guests with no write access
          if (msg == null) {
            DropDownLink<?> link = createDropDownLink(level, controller, prefix);
            levelLinks.add(link);
            panel.add(link, constrains);
          }
          else {
            JLabel noAccessLabel = createNoChangeLabel(level, prefix, msg);
            panel.add(noAccessLabel, constrains);
          }
        }
      }
    }
    panel.add(Box.createHorizontalGlue(), gc.next().fillCellHorizontally().weightx(1.0));

    controller.fillHectorPanels(panel, gc);

    panel.setOpaque(true);
    panel.setBackground(UIUtil.getToolTipActionBackground());
    panel.setBorder(JBUI.Borders.empty(4, 10));
    return panel;
  }

  private @NotNull DropDownLink<InspectionsLevel> createDropDownLink(@NotNull LanguageHighlightLevel level,
                                                                     @NotNull UIController controller,
                                                                     @NotNull @Nls String prefix) {
    return new DropDownLink<>(level.getLevel(),
                              controller.getAvailableLevels(),
                              inspectionsLevel -> {
                                controller.setHighLightLevel(new LanguageHighlightLevel(level.getLangID(), inspectionsLevel));
                                myContent.revalidate();

                                Dimension size = myContent.getPreferredSize();
                                size.width = Math.max(size.width, JBUIScale.scale(296));
                                myPopup.setSize(size);
                              },
                              true) {
      @NotNull
      @Override
      protected String itemToString(@NotNull InspectionsLevel item) {
        return prefix + item;
      }
    };
  }

  @NotNull
  private static JLabel createNoChangeLabel(@NotNull LanguageHighlightLevel level, @NotNull @Nls String prefix, @NotNull @Nls String msg) {
    JLabel label = new JLabel(prefix + level.getLevel());
    new HelpTooltip().setDescription(msg).installOn(label);
    return label;
  }


  private static final class MenuAction extends DefaultActionGroup implements HintManagerImpl.ActionToIgnore {
    private MenuAction(@NotNull List<? extends AnAction> actions, @NotNull AnAction compactViewAction) {
      setPopup(true);
      addAll(actions);
      add(compactViewAction);
    }
  }

  private static final class TrackableLinkLabel extends LinkLabel<Object> {
    private InputEvent myEvent;

    private TrackableLinkLabel(@NotNull @NlsContexts.LinkLabel String text, @NotNull Runnable action) {
      super(text, null);
      setListener((__, ___) -> {
        action.run();
        ActionsCollector.getInstance().record(null, myEvent, getClass());
      }, null);
    }

    @Override
    public void doClick(InputEvent e) {
      myEvent = e;
      super.doClick(e);
    }
  }
}