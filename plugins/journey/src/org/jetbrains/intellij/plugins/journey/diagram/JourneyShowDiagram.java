package org.jetbrains.intellij.plugins.journey.diagram;

import com.intellij.diagnostic.Checks;
import com.intellij.diagram.DiagramDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.uml.UmlVirtualFileSystem;
import com.intellij.uml.core.actions.ShowDiagram;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collections;

public class JourneyShowDiagram extends ShowDiagram {
  public void showDiagram(PsiElement file) {
    DiagramSeed seed = createSeed(file.getProject(), JourneyDiagramProvider.getInstance(), new JourneyNodeIdentity(file), Collections.emptyList());
    showUnderProgress(seed, new RelativePoint(new Point()));
  }

  public static VirtualFile getUmlVirtualFile() {
    final var url = UmlVirtualFileSystem.PROTOCOL_PREFIX + "JOURNEY/JourneyEditorTab";
    return VirtualFileManager.getInstance().findFileByUrl(url);
  }

  @Override
  @RequiresReadLock
  protected @NotNull UmlVirtualFileSystem.UmlVirtualFile initializeDiagramVirtualFile(@NotNull DiagramSeed seed) {
    final var element = seed.getOriginalElement();
    final var virtualFile = getUmlVirtualFile();
    Checks.check(virtualFile instanceof UmlVirtualFileSystem.UmlVirtualFile);

    final var file = (UmlVirtualFileSystem.UmlVirtualFile)virtualFile;
    if (file.getProject() == null) file.setProject(seed.getProject());
    file.putUserData(DiagramDataKeys.ORIGINAL_ELEMENT, element);
    return file;
  }
}
