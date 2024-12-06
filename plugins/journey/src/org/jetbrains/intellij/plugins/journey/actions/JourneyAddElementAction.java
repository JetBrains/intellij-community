package org.jetbrains.intellij.plugins.journey.actions;

import com.intellij.diagram.DiagramAction;
import com.intellij.diagram.DiagramBuilder;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.LayeredIcon;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramDataModel;

import javax.swing.*;

import java.util.Objects;

import static com.intellij.icons.AllIcons.Actions.AddFile;

public class JourneyAddElementAction extends DiagramAction {
  public static final String NAME = "Add psi element";

  private static final Icon ICON;
  static {
    LayeredIcon icon = new LayeredIcon(2);
    icon.setIcon(AddFile, 0);
    ICON = icon;
  }

  public JourneyAddElementAction() {
    super(NAME, NAME, ICON);
  }

  @Override
  public void perform(@NotNull AnActionEvent e) {
    final var builder = getBuilder(e);
    if (builder == null) return;

    Objects.requireNonNull(builder.getProvider().getExtras().getAddElementHandler()).actionPerformed(e);
  }

  @Override
  public boolean isEnabled(@NotNull AnActionEvent e, @NotNull DiagramBuilder b) {
    return b.getDataModel() instanceof JourneyDiagramDataModel;
  }

  @Override
  public @NotNull @Nls String getActionName() {
    return NAME;
  }
}
