package com.intellij.openapi.vcs.changes.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.JTextComponent;

/**
 * @author Dmitry Avdeev
 */
public interface EditChangelistSupport {

  ExtensionPointName<EditChangelistSupport> EP_NAME = ExtensionPointName.create("com.intellij.editChangelistSupport");

  void installSearch(JTextComponent name, JTextComponent comment);

  Consumer<LocalChangeList> addControls(JPanel bottomPanel, @Nullable LocalChangeList initial);
  void changelistCreated(LocalChangeList changeList);
}
