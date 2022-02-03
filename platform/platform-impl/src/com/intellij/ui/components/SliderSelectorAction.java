// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Consumer;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;

/**
 * @author Irina.Chernushina on 11/12/2014.
 */
public class SliderSelectorAction extends DumbAwareAction {
  @NotNull private final Configuration myConfiguration;

  public SliderSelectorAction(@Nullable @NlsActions.ActionText String text, @Nullable @NlsActions.ActionDescription String description, @Nullable Icon icon,
                              @NotNull Configuration configuration) {
    super(text, description, icon);
    myConfiguration = configuration;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    final String tooltip = myConfiguration.getTooltip();
    if (tooltip != null) {
      e.getPresentation().setText(getTemplatePresentation().getText() + " (" + tooltip + ")");
      e.getPresentation().setDescription(getTemplatePresentation().getDescription() + " (" + tooltip + ")");
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final JPanel result = new JPanel(new BorderLayout());
    final JLabel label = new JLabel(myConfiguration.getSelectText());
    label.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 0));
    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.add(label, BorderLayout.NORTH);

    final Map<Integer, JLabel> map = myConfiguration.getDictionary();
    final JSlider slider = new JSlider(SwingConstants.HORIZONTAL, myConfiguration.getMin(), myConfiguration.getMax(), myConfiguration.getSelected()) {
      Integer myWidth = null;
      @Override
      public Dimension getPreferredSize() {
        final Dimension size = super.getPreferredSize();
        if (myWidth == null) {
          myWidth = 10;
          final FontMetrics fm = getFontMetrics(getFont());
          for (JLabel confLabel : map.values()) {
            String text = confLabel.getText();
            myWidth += fm.stringWidth(text + "W");
          }
        }
        return new Dimension(myWidth, size.height);
      }
    };

    slider.setMinorTickSpacing(1);
    slider.setPaintTicks(true);
    slider.setPaintTrack(true);
    slider.setSnapToTicks(true);
    UIUtil.setSliderIsFilled(slider, true);
    slider.setPaintLabels(true);
    //noinspection UseOfObsoleteCollectionType
    slider.setLabelTable(new Hashtable<>(map));

    result.add(wrapper, BorderLayout.WEST);
    result.add(slider, BorderLayout.CENTER);

    final Runnable saveSelection = () -> {
      int value = slider.getModel().getValue();
      myConfiguration.getResultConsumer().consume(value);
    };
    final Ref<JBPopup> popupRef = new Ref<>(null);
    final JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(result, slider)
      .setMovable(true)
      .setCancelOnWindowDeactivation(true)
      .setCancelKeyEnabled(myConfiguration.isShowOk())
      .setKeyboardActions(Collections.singletonList(Pair.create(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          saveSelection.run();
          popupRef.get().closeOk(null);
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0))))
      .createPopup();

    popupRef.set(popup);
    if (myConfiguration.isShowOk()) {
      final JButton done = new JButton(IdeBundle.message("button.done"));
      final JBPanel doneWrapper = new JBPanel(new BorderLayout());
      doneWrapper.add(done, BorderLayout.NORTH);
      result.add(doneWrapper, BorderLayout.EAST);
      done.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          saveSelection.run();
          popup.closeOk(null);
        }
      });
    } else {
      popup.setFinalRunnable(saveSelection);
    }
    InputEvent inputEvent = e.getInputEvent();
    show(e, result, popup, inputEvent);
  }

  protected void show(AnActionEvent e, JPanel result, JBPopup popup, InputEvent inputEvent) {
    if (inputEvent instanceof MouseEvent) {
      int width = result.getPreferredSize().width;
      MouseEvent inputEvent1 = (MouseEvent)inputEvent;
      Point point1 = new Point(inputEvent1.getX() - width / 2, inputEvent1.getY());
      RelativePoint point = new RelativePoint(inputEvent1.getComponent(), point1);
      popup.show(point);
    } else {
      popup.showInBestPositionFor(e.getDataContext());
    }
  }

  public static class Configuration {
    @NotNull
    private final @Nls String mySelectText;
    @NotNull
    private final Map<Integer, JLabel> myDictionary;
    private final int mySelected;
    private final int myMin;
    private final int myMax;
    @NotNull
    private final Consumer<Integer> myResultConsumer;
    private boolean showOk = false;

    public Configuration(int selected, @NotNull Dictionary<Integer, @Nls String> dictionary, 
                         @NotNull @Nls String selectText, @NotNull Consumer<Integer> consumer) {
      mySelected = selected;
      myDictionary = new HashMap<>();
      mySelectText = selectText;
      myResultConsumer = consumer;

      int min = 1;
      int max = 0;
      final Enumeration<Integer> keys = dictionary.keys();
      while (keys.hasMoreElements()) {
        final Integer key = keys.nextElement();
        final String value = dictionary.get(key);
        myDictionary.put(key, markLabel(value));
        min = Math.min(min, key);
        max = Math.max(max, key);
      }
      myMin = min;
      myMax = max;
    }

    private static JLabel markLabel(final @Nls String text) {
      JLabel label = new JLabel(text);
      label.setFont(StartupUiUtil.getLabelFont());
      return label;
    }

    @NotNull
    public @Nls String getSelectText() {
      return mySelectText;
    }

    @NotNull
    public Map<Integer, JLabel> getDictionary() {
      return myDictionary;
    }

    @NotNull
    public Consumer<Integer> getResultConsumer() {
      return myResultConsumer;
    }

    public int getSelected() {
      return mySelected;
    }

    public int getMin() {
      return myMin;
    }

    public int getMax() {
      return myMax;
    }

    public boolean isShowOk() {
      return showOk;
    }

    public void setShowOk(boolean showOk) {
      this.showOk = showOk;
    }

    public @NlsContexts.Tooltip String getTooltip() {
      return null;
    }
  }
}
