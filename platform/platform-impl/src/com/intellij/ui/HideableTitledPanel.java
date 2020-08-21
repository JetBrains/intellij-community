package com.intellij.ui;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author evgeny zakrevsky
 */
public class HideableTitledPanel extends JPanel {

  private final HideableDecorator myDecorator;

  public HideableTitledPanel(@NlsContexts.Separator String title) {
    this(title, true);
  }

  public HideableTitledPanel(@NlsContexts.Separator String title, boolean adjustWindow) {
    super(new BorderLayout());
    myDecorator = new HideableDecorator(this, title, adjustWindow);
  }

  public HideableTitledPanel(@NlsContexts.Separator String title, JComponent content, boolean on) {
    this(title, true, content, on);
  }

  public HideableTitledPanel(@NlsContexts.Separator String title, boolean adjustWindow, JComponent content, boolean on) {
    this(title, adjustWindow);
    setContentComponent(content);
    setOn(on);
  }

  public void setContentComponent(@Nullable JComponent content) {
    myDecorator.setContentComponent(content);
  }

  public void setOn(boolean on) {
    myDecorator.setOn(on);
  }

  public boolean isExpanded() {
    return myDecorator.isExpanded();
  }

  public void setTitle(@NlsContexts.Separator String title) {
    myDecorator.setTitle(title);
  }

  public String getTitle() {
    return myDecorator.getTitle();
  }

  @Override
  public void setEnabled(boolean enabled) {
    myDecorator.setEnabled(enabled);
  }
}
