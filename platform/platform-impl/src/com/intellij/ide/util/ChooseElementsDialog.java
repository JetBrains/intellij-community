// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.*;

public abstract class ChooseElementsDialog<T> extends DialogWrapper {
  protected ElementsChooser<T> myChooser;
  private final @NlsContexts.Label String myDescription;

  public ChooseElementsDialog(Project project, List<? extends T> items, @NlsContexts.DialogTitle String title, @NlsContexts.Label String description) {
    this(project, items, title, description, false);
  }

  public ChooseElementsDialog(Project project, List<? extends T> items, @NlsContexts.DialogTitle String title, @NlsContexts.Label String description, boolean sort) {
    super(project, true);
    myDescription = description;
    initializeDialog(items, title, sort);
  }

  public ChooseElementsDialog(Component parent, List<? extends T> items, @NlsContexts.DialogTitle String title) {
    this(parent, items, title, null, false);
  }

  public ChooseElementsDialog(Component parent, List<? extends T> items, @NlsContexts.DialogTitle String title, @Nullable @NlsContexts.Label String description, final boolean sort) {
    super(parent, true);
    myDescription = description;
    initializeDialog(items, title, sort);
  }

  /**
   * @return true if elements should have checkboxes
   * @see #getMarkedElements()
   * @see #isElementMarkedByDefault(Object)
   */
  protected boolean canElementsBeMarked() {
    return false;
  }

  private void initializeDialog(final List<? extends T> items, @NlsContexts.DialogTitle String title, boolean sort) {
    setTitle(title);
    myChooser = new ElementsChooser<>(canElementsBeMarked()) {
      @Override
      protected String getItemText(@NotNull final T item) {
        return ChooseElementsDialog.this.getItemText(item);
      }
    };
    myChooser.setColorUnmarkedElements(false);

    List<? extends T> elements = new ArrayList<T>(items);
    if (sort) {
      elements.sort((Comparator<T>)(o1, o2) -> getItemText(o1).compareToIgnoreCase(getItemText(o2)));
    }
    setElements(elements, ContainerUtil.getFirstItems(elements, 1));
    myChooser.getComponent().registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        doOKAction();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED);

    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(@NotNull MouseEvent e) {
        doOKAction();
        return true;
      }
    }.installOn(myChooser.getComponent());

    init();
  }

  @NotNull
  public List<T> showAndGetResult() {
    show();
    return getChosenElements();
  }

  protected abstract @NlsContexts.ListItem String getItemText(T item);

  @Nullable
  protected abstract Icon getItemIcon(T item);

  /**
   * Override this method and return non-null value to specify location of {@code item}.
   * It will be shown as grayed text next to the {@link #getItemText(T) item text}.
   */
  protected @Nls String getItemLocation(T item) {
    return null; // default implementation
  }

  @NotNull
  public List<T> getChosenElements() {
    return isOK() ? myChooser.getSelectedElements() : Collections.emptyList();
  }

  public void selectElements(@NotNull List<? extends T> elements) {
    myChooser.selectElements(elements);
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myChooser.getComponent();
  }

  @Override
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(ScrollPaneFactory.createScrollPane(myChooser.getComponent()), BorderLayout.CENTER);
    if (myDescription != null) {
      panel.add(new JLabel(myDescription), BorderLayout.NORTH);
    }
    return panel;
  }

  private void setElements(final Collection<? extends T> elements, final Collection<? extends T> elementsToSelect) {
    myChooser.clear();
    for (final T item : elements) {
      myChooser.addElement(item, isElementMarkedByDefault(item), createElementProperties(item));
    }
    myChooser.selectElements(elementsToSelect);
  }

  /**
   * @return true if element's checkbox should be selected by default
   * Takes effect if {@link #canElementsBeMarked()} returns true
   */
  protected boolean isElementMarkedByDefault(T element) {
    return false;
  }

  /**
   * @return list of elements with selected checkboxes
   * Works only if {@link #canElementsBeMarked()} returns true
   * @see #isElementMarkedByDefault(Object)
   */
  public List<T> getMarkedElements() {
    return myChooser.getMarkedElements();
  }

  private ElementsChooser.ElementProperties createElementProperties(final T item) {
    return new ElementsChooser.ElementProperties() {
      @Override
      @Nullable
      public Icon getIcon() {
        return getItemIcon(item);
      }

      @Override
      @Nullable
      public @Nls String getLocation() {
        return getItemLocation(item);
      }
    };
  }
}
