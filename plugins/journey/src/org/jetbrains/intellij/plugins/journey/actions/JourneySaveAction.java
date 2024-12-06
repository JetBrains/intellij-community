package org.jetbrains.intellij.plugins.journey.actions;

import com.intellij.diagram.DiagramAction;
import com.intellij.diagram.DiagramBuilder;
import com.intellij.diagram.DiagramFileEditor;
import com.intellij.diagram.presentation.DiagramState;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.ui.LayeredIcon;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.intellij.plugins.journey.diagram.JourneyDiagramDataModel;

import javax.swing.*;

import static com.intellij.icons.AllIcons.Actions.MenuSaveall;

public class JourneySaveAction extends DiagramAction {
  private static final Logger LOG = Logger.getInstance(JourneySaveAction.class);
  public static final String NAME = "Save journey";

  private static final Icon ICON;
  static {
    LayeredIcon icon = new LayeredIcon(2);
    icon.setIcon(MenuSaveall, 0);
    ICON = icon;
  }

  public JourneySaveAction() {
    super(NAME, NAME, ICON);
  }

  @Override
  public void perform(@NotNull AnActionEvent e) {
    final var builder = getBuilder(e);
    if (builder == null) return;

    final DiagramState state = DiagramState.makeBuilderSnapshot(builder);
    final Document xml = new Document(new Element("Diagram"));
    state.write(xml.getRootElement());

    final FileEditor fileEditor = e.getData(PlatformCoreDataKeys.FILE_EDITOR);
    if (fileEditor instanceof DiagramFileEditor editor) {
      if (!state.saveTo(editor.getOriginalVirtualFile(), builder.getProject())) {
        LOG.error("Can't save");
      }
    }
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
