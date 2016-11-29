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
package com.intellij.ui.components;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;

/**
 * @author Irina.Chernushina on 11/12/2014.
 */
public class SliderSelectorAction extends DumbAwareAction {
  @NotNull private final Configuration myConfiguration;

  public SliderSelectorAction(@Nullable String text, @Nullable String description, @Nullable Icon icon,
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

    final Dictionary dictionary = myConfiguration.getDictionary();
    final Enumeration elements = dictionary.elements();
    final JSlider slider = new JSlider(SwingConstants.HORIZONTAL, myConfiguration.getMin(), myConfiguration.getMax(), myConfiguration.getSelected()) {
      Integer myWidth = null;
      @Override
      public Dimension getPreferredSize() {
        final Dimension size = super.getPreferredSize();
        if (myWidth == null) {
          myWidth = 10;
          final FontMetrics fm = getFontMetrics(getFont());
          while (elements.hasMoreElements()) {
            String text = ((JLabel)elements.nextElement()).getText();
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
    slider.setLabelTable(dictionary);

    if (! myConfiguration.isShowOk()) {
      result.add(wrapper, BorderLayout.WEST);
      result.add(slider, BorderLayout.CENTER);
    } else {
      result.add(wrapper, BorderLayout.WEST);
      result.add(slider, BorderLayout.CENTER);
    }

    final Runnable saveSelection = () -> {
      int value = slider.getModel().getValue();
      myConfiguration.getResultConsumer().consume(value);
    };
    final Ref<JBPopup> popupRef = new Ref<>(null);
    final JBPopup popup = JBPopupFactory.getInstance().createComponentPopupBuilder(result, slider)
      .setMovable(true)
      .setCancelOnWindowDeactivation(true)
      .setCancelKeyEnabled(myConfiguration.isShowOk())
      .setKeyboardActions(Collections.singletonList(Pair.<ActionListener, KeyStroke>create(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          saveSelection.run();
          popupRef.get().closeOk(null);
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0))))
      .createPopup();

    popupRef.set(popup);
    if (myConfiguration.isShowOk()) {
      final JButton done = new JButton("Done");
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
    private final String mySelectText;
    @NotNull
    private final Dictionary myDictionary;
    private final int mySelected;
    private final int myMin;
    private final int myMax;
    @NotNull
    private final Consumer<Integer> myResultConsumer;
    private boolean showOk = false;

    public Configuration(int selected, @NotNull Dictionary dictionary, @NotNull String selectText, @NotNull Consumer<Integer> consumer) {
      mySelected = selected;
      myDictionary = new Hashtable<Integer, JComponent>();
      mySelectText = selectText;
      myResultConsumer = consumer;

      int min = 1;
      int max = 0;
      final Enumeration keys = dictionary.keys();
      while (keys.hasMoreElements()) {
        final Integer key = (Integer)keys.nextElement();
        final String value = (String)dictionary.get(key);
        myDictionary.put(key, markLabel(value));
        min = Math.min(min, key);
        max = Math.max(max, key);
      }
      myMin = min;
      myMax = max;
    }

    private static JLabel markLabel(final String text) {
      JLabel label = new JLabel(text);
      label.setFont(UIUtil.getLabelFont());
      return label;
    }

    @NotNull
    public String getSelectText() {
      return mySelectText;
    }

    @NotNull
    public Dictionary getDictionary() {
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

    public String getTooltip() {
      return null;
    }
  }
}
